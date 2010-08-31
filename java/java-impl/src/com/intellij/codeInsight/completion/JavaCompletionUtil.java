/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.completion.scope.CompletionElement;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.element.ExcludeDeclaredFilter;
import com.intellij.psi.filters.element.ExcludeSillyAssignment;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.ClassCandidateInfo;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class JavaCompletionUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.JavaCompletionUtil");
  public static final Key<PairFunction<PsiExpression, CompletionParameters, PsiType>> DYNAMIC_TYPE_EVALUATOR = Key.create("DYNAMIC_TYPE_EVALUATOR");

  static final Key<PsiType> QUALIFIER_TYPE_ATTR = Key.create("qualifierType"); // SmartPsiElementPointer to PsiType of "qualifier"
  @NonNls
  public static final String GET_PREFIX = "get";
  @NonNls
  public static final String SET_PREFIX = "set";
  @NonNls
  public static final String IS_PREFIX = "is";
  private static final int MAX_SCOPE_SIZE_TO_SEARCH_UNRESOLVED = 50000;
  public static final OffsetKey LPAREN_OFFSET = OffsetKey.create("lparen");
  public static final OffsetKey RPAREN_OFFSET = OffsetKey.create("rparen");
  public static final OffsetKey ARG_LIST_END_OFFSET = OffsetKey.create("argListEnd");
  static final NullableLazyKey<ExpectedTypeInfo[], CompletionLocation> EXPECTED_TYPES = NullableLazyKey.create("expectedTypes", new NullableFunction<CompletionLocation, ExpectedTypeInfo[]>() {
    @Nullable
    public ExpectedTypeInfo[] fun(final CompletionLocation location) {
      return JavaSmartCompletionContributor.getExpectedTypes(location.getCompletionParameters());
    }
  });
  private static final PsiElementPattern.Capture<PsiElement> LEFT_PAREN = psiElement(JavaTokenType.LPARENTH).andOr(psiElement().withParent(
      PsiExpressionList.class), psiElement().afterLeaf(".", PsiKeyword.NEW));

  public static final Key<Boolean> SUPER_METHOD_PARAMETERS = Key.create("SUPER_METHOD_PARAMETERS");

  @Nullable
  public static Set<PsiType> getExpectedTypes(final CompletionParameters parameters) {
    final PsiExpression expr = PsiTreeUtil.getContextOfType(parameters.getPosition(), PsiExpression.class, true);
    if (expr != null) {
      final ExpectedTypeInfo[] expectedInfos = JavaSmartCompletionContributor.getExpectedTypes(parameters);
      if(expectedInfos != null){
        final Set<PsiType> set = new THashSet<PsiType>();
        for (final ExpectedTypeInfo expectedInfo : expectedInfos) {
          set.add(expectedInfo.getType());
        }
        return set;
      }
    }
    return null;
  }

  static final NullableLazyKey<PsiMethod, CompletionLocation> POSITION_METHOD = NullableLazyKey.create("positionMethod", new NullableFunction<CompletionLocation, PsiMethod>() {
    public PsiMethod fun(final CompletionLocation location) {
      return PsiTreeUtil.getParentOfType(location.getCompletionParameters().getPosition(), PsiMethod.class, false);
    }
  });
  public static final Key<List<PsiMethod>> ALL_METHODS_ATTRIBUTE = Key.create("allMethods");

  public static void completeLocalVariableName(Set<LookupElement> set, PrefixMatcher matcher, PsiVariable var){
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(var.getProject());
    final VariableKind variableKind = codeStyleManager.getVariableKind(var);

    String propertyName = null;
    if (variableKind == VariableKind.PARAMETER) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(var, PsiMethod.class);
      propertyName = PropertyUtil.getPropertyName(method);
    }

    final PsiType type = var.getType();
    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(variableKind, propertyName, null, type);
    final String[] suggestedNames = suggestedNameInfo.names;
    tunePreferencePolicy(LookupItemUtil.addLookupItems(set, suggestedNames, matcher), suggestedNameInfo);
    if (set.isEmpty()) {
      if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) && matcher.prefixMatches("object")) {
        set.add(LookupElementBuilder.create("object"));
      }
      if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING) && matcher.prefixMatches("string")) {
        set.add(LookupElementBuilder.create("string"));
      }
    }

    if (set.isEmpty()) {
      suggestedNameInfo = new SuggestedNameInfo(getOverlappedNameVersions(matcher.getPrefix(), suggestedNames, "")) {
        public void nameChoosen(String name) {
        }
      };

      tunePreferencePolicy(LookupItemUtil.addLookupItems(set, suggestedNameInfo.names, matcher), suggestedNameInfo);
    }
    PsiElement parent = PsiTreeUtil.getParentOfType(var, PsiCodeBlock.class);
    if(parent == null) parent = PsiTreeUtil.getParentOfType(var, PsiMethod.class);
    tunePreferencePolicy(LookupItemUtil.addLookupItems(set, getUnresolvedReferences(parent, false), matcher), suggestedNameInfo);
    final String[] nameSuggestions =
      JavaStatisticsManager.getNameSuggestions(type, JavaStatisticsManager.getContext(var), matcher.getPrefix());
    tunePreferencePolicy(LookupItemUtil.addLookupItems(set, nameSuggestions, matcher), suggestedNameInfo);
  }

  public static void completeFieldName(Set<LookupElement> set, PsiVariable var, final PrefixMatcher matcher){
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");

    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(var.getProject());
    final VariableKind variableKind = JavaCodeStyleManager.getInstance(var.getProject()).getVariableKind(var);

    final String prefix = matcher.getPrefix();
    if (PsiType.VOID.equals(var.getType()) || prefix.startsWith(IS_PREFIX) ||
        prefix.startsWith(GET_PREFIX) ||
        prefix.startsWith(SET_PREFIX)) {
      completeVariableNameForRefactoring(var.getProject(), set, matcher, var.getType(), variableKind);
      return;
    }

    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(variableKind, null, null, var.getType());
    final String[] suggestedNames = suggestedNameInfo.names;
    tunePreferencePolicy(LookupItemUtil.addLookupItems(set, suggestedNames, matcher), suggestedNameInfo);

    if (set.isEmpty()) {
      // use suggested names as suffixes
      final String requiredSuffix = codeStyleManager.getSuffixByVariableKind(variableKind);
      if(variableKind != VariableKind.STATIC_FINAL_FIELD){
        for (int i = 0; i < suggestedNames.length; i++)
          suggestedNames[i] = codeStyleManager.variableNameToPropertyName(suggestedNames[i], variableKind);
      }


        suggestedNameInfo = new SuggestedNameInfo(getOverlappedNameVersions(prefix, suggestedNames, requiredSuffix)) {
        public void nameChoosen(String name) {
        }
      };

      tunePreferencePolicy(LookupItemUtil.addLookupItems(set, suggestedNameInfo.names, matcher), suggestedNameInfo);
    }

    tunePreferencePolicy(LookupItemUtil.addLookupItems(set, JavaStatisticsManager.getNameSuggestions(var.getType(), JavaStatisticsManager.getContext(var), matcher.getPrefix()), matcher), suggestedNameInfo);
    tunePreferencePolicy(LookupItemUtil.addLookupItems(set, getUnresolvedReferences(var.getParent(), false),
                                                                      matcher), suggestedNameInfo);
  }

  public static void completeMethodName(Set<LookupElement> set, PsiElement element, final PrefixMatcher matcher){
    if(element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      if (method.isConstructor()) {
        final PsiClass containingClass = method.getContainingClass();
        final String name = containingClass.getName();
        if (StringUtil.isNotEmpty(name)) {
          LookupItemUtil.addLookupItem(set, name, matcher);
        }
        return;
      }
    }

    PsiClass ourClassParent = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (ourClassParent == null) return;
    LookupItemUtil.addLookupItems(set, getUnresolvedReferences(ourClassParent, true), matcher);

    if(!((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.PRIVATE)){
      LookupItemUtil.addLookupItems(set, getOverides(ourClassParent, PsiUtil.getTypeByPsiElement(element)),
                                    matcher);
      LookupItemUtil.addLookupItems(set, getImplements(ourClassParent, PsiUtil.getTypeByPsiElement(element)),
                                    matcher);
    }
    LookupItemUtil.addLookupItems(set, getPropertiesHandlersNames(
      ourClassParent,
      ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC),
      PsiUtil.getTypeByPsiElement(element), element), matcher);
  }

  public static PsiType getQualifierType(LookupItem item) {
    return item.getUserData(QUALIFIER_TYPE_ATTR);
  }

  public static void completeVariableNameForRefactoring(Project project, Set<LookupElement> set, String prefix, PsiType varType, VariableKind varKind) {
    completeVariableNameForRefactoring(project, set, new CamelHumpMatcher(prefix), varType, varKind);
  }

  public static void completeVariableNameForRefactoring(Project project, Set<LookupElement> set, PrefixMatcher matcher, PsiType varType, VariableKind varKind) {
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(varKind, null, null, varType);
    final String[] strings = completeVariableNameForRefactoring(codeStyleManager, matcher, varType, varKind, suggestedNameInfo);
    tunePreferencePolicy(LookupItemUtil.addLookupItems(set, strings, matcher), suggestedNameInfo);
  }

  public static String[] completeVariableNameForRefactoring(JavaCodeStyleManager codeStyleManager, final PsiType varType,
                                                               final VariableKind varKind,
                                                               SuggestedNameInfo suggestedNameInfo) {
    return completeVariableNameForRefactoring(codeStyleManager, new CamelHumpMatcher(""), varType, varKind, suggestedNameInfo);
  }

  public static String[] completeVariableNameForRefactoring(JavaCodeStyleManager codeStyleManager, final PrefixMatcher matcher, final PsiType varType,
                                                               final VariableKind varKind,
                                                               SuggestedNameInfo suggestedNameInfo) {
    Set<String> result = new LinkedHashSet<String>();
    final String[] suggestedNames = suggestedNameInfo.names;
    for (final String suggestedName : suggestedNames) {
      if (matcher.prefixMatches(suggestedName)) {
        result.add(suggestedName);
      }
    }

    if (result.isEmpty() && PsiType.VOID != varType) {
      // use suggested names as suffixes
      final String requiredSuffix = codeStyleManager.getSuffixByVariableKind(varKind);
      final String prefix = matcher.getPrefix();
      final boolean isMethodPrefix = prefix.startsWith(IS_PREFIX) || prefix.startsWith(GET_PREFIX) || prefix.startsWith(SET_PREFIX);
      if (varKind != VariableKind.STATIC_FINAL_FIELD || isMethodPrefix) {
        for (int i = 0; i < suggestedNames.length; i++) {
          suggestedNames[i] = codeStyleManager.variableNameToPropertyName(suggestedNames[i], varKind);
        }
      }

      ContainerUtil.addAll(result, getOverlappedNameVersions(prefix, suggestedNames, requiredSuffix));


    }
    return ArrayUtil.toStringArray(result);
  }

  private static void tunePreferencePolicy(final List<LookupElement> list, final SuggestedNameInfo suggestedNameInfo) {
    final InsertHandler insertHandler = new InsertHandler() {
      public void handleInsert(final InsertionContext context, final LookupElement item) {
        suggestedNameInfo.nameChoosen(item.getLookupString());
      }
    };

    for (int i = 0; i < list.size(); i++) {
      LookupElement item = list.get(i);
      if (item instanceof LookupItem) {
        ((LookupItem)item).setPriority(list.size() - i).setInsertHandler(insertHandler);
      }
    }
  }

  public static String[] getOverlappedNameVersions(final String prefix, final String[] suggestedNames, String suffix) {
    final List<String> newSuggestions = new ArrayList<String>();
    int longestOverlap = 0;

    for (String suggestedName : suggestedNames) {
      if (suggestedName.toUpperCase().startsWith(prefix.toUpperCase())) {
        newSuggestions.add(suggestedName);
        longestOverlap = prefix.length();
      }

      suggestedName = String.valueOf(Character.toUpperCase(suggestedName.charAt(0))) + suggestedName.substring(1);
      final int overlap = getOverlap(suggestedName, prefix);

      if (overlap < longestOverlap) continue;

      if (overlap > longestOverlap) {
        newSuggestions.clear();
        longestOverlap = overlap;
      }

      String suggestion = prefix.substring(0, prefix.length() - overlap) + suggestedName;

      final int lastIndexOfSuffix = suggestion.lastIndexOf(suffix);
      if (lastIndexOfSuffix >= 0 && suffix.length() < suggestion.length() - lastIndexOfSuffix) {
        suggestion = suggestion.substring(0, lastIndexOfSuffix) + suffix;
      }

      if (!newSuggestions.contains(suggestion)) {
        newSuggestions.add(suggestion);
      }
    }
    return ArrayUtil.toStringArray(newSuggestions);
  }

  static int getOverlap(final String propertyName, final String prefix) {
    int overlap = 0;
    int propertyNameLen = propertyName.length();
    int prefixLen = prefix.length();
    for (int j = 1; j < prefixLen && j < propertyNameLen; j++) {
      if (prefix.substring(prefixLen - j).equals(propertyName.substring(0, j))) {
        overlap = j;
      }
    }
    return overlap;
  }

  public static PsiType eliminateWildcards(PsiType type) {
    return eliminateWildcardsInner(type, true);
  }

  static PsiType eliminateWildcardsInner(PsiType type, final boolean eliminateInTypeArguments) {
    if (eliminateInTypeArguments && type instanceof PsiClassType) {
      PsiClassType classType = ((PsiClassType)type);
      JavaResolveResult resolveResult = classType.resolveGenerics();
      PsiClass aClass = (PsiClass)resolveResult.getElement();
      if (aClass != null) {
        PsiManager manager = aClass.getManager();
        PsiTypeParameter[] typeParams = aClass.getTypeParameters();
        Map<PsiTypeParameter, PsiType> map = new HashMap<PsiTypeParameter, PsiType>();
        for (PsiTypeParameter typeParam : typeParams) {
          PsiType substituted = resolveResult.getSubstitutor().substitute(typeParam);
          if (substituted instanceof PsiWildcardType) {
            substituted = ((PsiWildcardType)substituted).getBound();
            if (substituted == null) substituted = PsiType.getJavaLangObject(manager, aClass.getResolveScope());
          }
          map.put(typeParam, substituted);
        }

        PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        PsiSubstitutor substitutor = factory.createSubstitutor(map);
        type = factory.createType(aClass, substitutor);
      }
    }
    else if (type instanceof PsiArrayType) {
      return eliminateWildcardsInner(((PsiArrayType)type).getComponentType(), false).createArrayType();
    }
    else if (type instanceof PsiWildcardType) {
      return ((PsiWildcardType)type).getExtendsBound();
    }
    return type;
  }

  public static String[] getOverides(final PsiClass parent, final PsiType typeByPsiElement) {
    final List<String> overides = new ArrayList<String>();
    final Collection<CandidateInfo> methodsToOverrideImplement = OverrideImplementUtil.getMethodsToOverrideImplement(parent, true);
    for (final CandidateInfo candidateInfo : methodsToOverrideImplement) {
      final PsiElement element = candidateInfo.getElement();
      if (Comparing.equal(typeByPsiElement, PsiUtil.getTypeByPsiElement(element)) && element instanceof PsiNamedElement) {
        overides.add(((PsiNamedElement)element).getName());
      }
    }
    return ArrayUtil.toStringArray(overides);
  }

  public static String[] getImplements(final PsiClass parent, final PsiType typeByPsiElement) {
    final List<String> overides = new ArrayList<String>();
    final Collection<CandidateInfo> methodsToOverrideImplement = OverrideImplementUtil.getMethodsToOverrideImplement(parent, false);
    for (final CandidateInfo candidateInfo : methodsToOverrideImplement) {
      final PsiElement element = candidateInfo.getElement();
      if (Comparing.equal(typeByPsiElement,PsiUtil.getTypeByPsiElement(element)) && element instanceof PsiNamedElement) {
        overides.add(((PsiNamedElement)element).getName());
      }
    }
    return ArrayUtil.toStringArray(overides);
  }

  public static String[] getPropertiesHandlersNames(final PsiClass psiClass,
                                                    final boolean staticContext,
                                                    final PsiType varType,
                                                    final PsiElement element) {
    class Change implements Runnable {
      private String[] result;

      public void run() {
        final List<String> propertyHandlers = new ArrayList<String>();
        final PsiField[] fields = psiClass.getFields();

        for (final PsiField field : fields) {
          if (field == element) continue;
          final PsiModifierList modifierList = field.getModifierList();
          if (staticContext && (modifierList != null && !modifierList.hasModifierProperty(PsiModifier.STATIC))) continue;

          if (field.getType().equals(varType)) {
            final String getterName = PropertyUtil.suggestGetterName(field.getProject(), field);
            if ((psiClass.findMethodsByName(getterName, true).length == 0 ||
                 psiClass.findMethodBySignature(PropertyUtil.generateGetterPrototype(field), true) == null)) {
              propertyHandlers.add(getterName);
            }
          }

          if (PsiType.VOID.equals(varType)) {
            final String setterName = PropertyUtil.suggestSetterName(field.getProject(), field);
            if ((psiClass.findMethodsByName(setterName, true).length == 0 ||
                 psiClass.findMethodBySignature(PropertyUtil.generateSetterPrototype(field), true) == null)) {
              propertyHandlers.add(setterName);
            }
          }
        }
        result = ArrayUtil.toStringArray(propertyHandlers);
      }
    }
    final Change result = new Change();
    element.getManager().performActionWithFormatterDisabled(result);
    return result.result;
  }

  public static boolean isInExcludedPackage(@NotNull final PsiClass psiClass) {
    final String name = psiClass.getQualifiedName();
    if (name == null) return false;
    CodeInsightSettings cis = CodeInsightSettings.getInstance();
    for (String excluded : cis.EXCLUDED_PACKAGES) {
      if (name.equals(excluded) || name.startsWith(excluded + ".")) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static PsiField getOriginalElement(@NotNull PsiField field) {
    final PsiClass cls = field.getContainingClass();
    if (cls != null) {
      final PsiClass newParent = getOriginalElement(cls);
      if (newParent != cls) {
        final PsiField original = newParent.findFieldByName(field.getName(), false);
        if (original != null) {
          return original;
        }
      }
    }
    return field;
  }

  @NotNull
  public static PsiTypeParameter getOriginalElement(@NotNull PsiTypeParameter param) {
    final PsiClass parent = PsiTreeUtil.getParentOfType(param, PsiClass.class, true, PsiMethod.class);
    if (parent != null) {
      final PsiClass newParent = getOriginalElement(parent);
      if (newParent != parent) {
        for (PsiTypeParameter parameter : newParent.getTypeParameters()) {
          if (parameter.getName().equals(param.getName())) {
            return parameter;
          }
        }
      }
    }
    return param;
  }

  @NotNull
  public static PsiClass getOriginalElement(@NotNull PsiClass cls) {
    final PsiClass containingClass = cls.getContainingClass();
    if (containingClass != null) {
      final PsiClass newParent = getOriginalElement(containingClass);
      if (newParent != containingClass) {
        return findClassByName(cls, newParent.getInnerClasses());
      }
    }

    final PsiFile containingFile = cls.getContainingFile();
    if (containingFile instanceof PsiClassOwner) {
      return findClassByName(cls, ((PsiClassOwner)containingFile.getOriginalFile()).getClasses());
    }

    return cls;
  }

  private static PsiClass findClassByName(PsiClass defResult, PsiClass[] classes) {
    String name = defResult.getName();
    if (name == null) return defResult;

    for (PsiClass candidate : classes) {
      if (name.equals(candidate.getName())) {
        return candidate;
      }
    }
    return defResult;
  }

  @SuppressWarnings({"unchecked"})
  @NotNull
  public static <T extends PsiType> T originalize(@NotNull T type) {
    if (!type.isValid()) {
      return type;
    }

    return (T)type.accept(new PsiTypeVisitor<PsiType>() {

      public PsiType visitArrayType(final PsiArrayType arrayType) {
        return new PsiArrayType(originalize(arrayType.getComponentType()));
      }

      public PsiType visitCapturedWildcardType(final PsiCapturedWildcardType capturedWildcardType) {
        return PsiCapturedWildcardType.create(originalize(capturedWildcardType.getWildcard()), capturedWildcardType.getContext());
      }

      public PsiType visitClassType(final PsiClassType classType) {
        final PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
        final PsiClass psiClass = classResolveResult.getElement();
        final PsiSubstitutor substitutor = classResolveResult.getSubstitutor();
        if (psiClass == null) return classType;

        LOG.assertTrue(psiClass.isValid());

        return new PsiImmediateClassType(getOriginalElement(psiClass), originalize(substitutor));
      }

      public PsiType visitEllipsisType(final PsiEllipsisType ellipsisType) {
        return new PsiEllipsisType(originalize(ellipsisType.getComponentType()));
      }

      public PsiType visitPrimitiveType(final PsiPrimitiveType primitiveType) {
        return primitiveType;
      }

      public PsiType visitType(final PsiType type) {
        return type;
      }

      public PsiType visitWildcardType(final PsiWildcardType wildcardType) {
        final PsiType bound = wildcardType.getBound();
        final PsiManager manager = wildcardType.getManager();
        if (bound == null) return PsiWildcardType.createUnbounded(manager);
        return wildcardType.isExtends() ? PsiWildcardType.createExtends(manager, bound) : PsiWildcardType.createSuper(manager, bound);
      }
    });
  }

  @Nullable
  private static PsiSubstitutor originalize(@Nullable final PsiSubstitutor substitutor) {
    if (substitutor == null) return null;

    PsiSubstitutor originalSubstitutor = PsiSubstitutor.EMPTY;
    for (final Map.Entry<PsiTypeParameter, PsiType> entry : substitutor.getSubstitutionMap().entrySet()) {
      final PsiType value = entry.getValue();
      originalSubstitutor = originalSubstitutor.put(getOriginalElement(entry.getKey()), value == null ? null : originalize(value));
    }
    return originalSubstitutor;
  }

  public static String[] getUnresolvedReferences(final PsiElement parentOfType, final boolean referenceOnMethod) {
    if (parentOfType != null && parentOfType.getTextLength() > MAX_SCOPE_SIZE_TO_SEARCH_UNRESOLVED) return ArrayUtil.EMPTY_STRING_ARRAY;
    final List<String> unresolvedRefs = new ArrayList<String>();

    if (parentOfType != null) {
      parentOfType.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitReferenceExpression(PsiReferenceExpression reference) {
          final PsiElement parent = reference.getParent();
          if (parent instanceof PsiReference) return;
          if (referenceOnMethod && parent instanceof PsiMethodCallExpression &&
              reference == ((PsiMethodCallExpression)parent).getMethodExpression()) {
            if (reference.resolve() == null && reference.getReferenceName() != null) unresolvedRefs.add(reference.getReferenceName());
          }
          else if (!referenceOnMethod && !(parent instanceof PsiMethodCallExpression) &&reference.resolve() == null && reference.getReferenceName() != null) {
            unresolvedRefs.add(reference.getReferenceName());
          }
        }
      });
    }
    return ArrayUtil.toStringArray(unresolvedRefs);
  }

  public static void initOffsets(final PsiFile file, final Project project, final OffsetMap offsetMap){
    int selectionEndOffset = offsetMap.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);

    PsiElement element = file.findElementAt(selectionEndOffset);
    if (element == null) return;

    if (LEFT_PAREN.accepts(element)) {
      selectionEndOffset--;
      element = file.findElementAt(selectionEndOffset);
      if (element == null) return;
    }

    final PsiReference reference = file.findReferenceAt(selectionEndOffset);
    if(reference != null) {
      if(reference instanceof PsiJavaCodeReferenceElement){
        offsetMap.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, element.getParent().getTextRange().getEndOffset());
      }
      else{
        offsetMap.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET,
                                 reference.getElement().getTextRange().getStartOffset() + reference.getRangeInElement().getEndOffset());
      }

      element = file.findElementAt(offsetMap.getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET));
    }
    else if (isWord(element)){
      if(element instanceof PsiIdentifier && element.getParent() instanceof PsiJavaCodeReferenceElement){
        offsetMap.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, element.getParent().getTextRange().getEndOffset());
      }
      else{
        offsetMap.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, element.getTextRange().getEndOffset());
      }

      element = file.findElementAt(offsetMap.getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET));
      if (element == null) return;
    }

    if (element instanceof PsiWhiteSpace &&
        ( !element.textContains('\n') ||
          CodeStyleSettingsManager.getInstance(project).getCurrentSettings().METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE
        )
       ){
      element = file.findElementAt(element.getTextRange().getEndOffset());
    }

    if (LEFT_PAREN.accepts(element)) {
      offsetMap.addOffset(LPAREN_OFFSET, element.getTextRange().getStartOffset());
      PsiElement list = element.getParent();
      PsiElement last = list.getLastChild();
      if (last instanceof PsiJavaToken && ((PsiJavaToken)last).getTokenType() == JavaTokenType.RPARENTH) {
        offsetMap.addOffset(RPAREN_OFFSET, last.getTextRange().getStartOffset());
      }


      offsetMap.addOffset(ARG_LIST_END_OFFSET, list.getTextRange().getEndOffset());
    }

  }

  static boolean isWord(PsiElement element) {
    if (element instanceof PsiIdentifier){
      return true;
    }
    else if (element instanceof PsiKeyword){
      return true;
    }
    else if (element instanceof PsiJavaToken){
      final String text = element.getText();
      if(PsiKeyword.TRUE.equals(text)) return true;
      if(PsiKeyword.FALSE.equals(text)) return true;
      if(PsiKeyword.NULL.equals(text)) return true;
      return false;
    }
    else if (element instanceof PsiDocToken) {
      IElementType tokenType = ((PsiDocToken)element).getTokenType();
      return tokenType == JavaDocTokenType.DOC_TAG_VALUE_TOKEN || tokenType == JavaDocTokenType.DOC_TAG_NAME;
    }
    else if (element instanceof XmlToken) {
      IElementType tokenType = ((XmlToken)element).getTokenType();
      return tokenType == XmlTokenType.XML_TAG_NAME ||
          tokenType == XmlTokenType.XML_NAME ||
          tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN ||
          // html data chars contains whitespaces
          (tokenType == XmlTokenType.XML_DATA_CHARACTERS && !(element.getParent() instanceof HtmlTag));
    }
    else{
      return false;
    }
  }

  public static void resetParensInfo(final OffsetMap offsetMap) {
    offsetMap.removeOffset(LPAREN_OFFSET);
    offsetMap.removeOffset(RPAREN_OFFSET);
    offsetMap.removeOffset(ARG_LIST_END_OFFSET);
    offsetMap.removeOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET);
  }

  @Nullable
  public static List<? extends PsiElement> getAllPsiElements(final LookupElement item) {
    List<PsiMethod> allMethods = item.getUserData(ALL_METHODS_ATTRIBUTE);
    if (allMethods != null) return allMethods;
    if (item.getObject() instanceof PsiElement) return Arrays.asList((PsiElement)item.getObject());
    return null;
  }

  @Nullable
  private static PsiType getPsiType(final Object o) {
    if (o instanceof PsiVariable) {
      return ((PsiVariable)o).getType();
    }
    else if (o instanceof PsiMethod) {
      return ((PsiMethod)o).getReturnType();
    }
    else if (o instanceof PsiClass) {
      final PsiClass psiClass = (PsiClass)o;
      return JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory().createType(psiClass);
    }
    else if (o instanceof PsiExpression) {
      return ((PsiExpression)o).getType();
    }
    return null;
  }

  public static int getNameEndMatchingDegree(final String name, ExpectedTypeInfo[] expectedInfos, String prefix) {
    int res = 0;
    if (name != null && expectedInfos != null) {
      if (prefix.equals(name)) {
        res = Integer.MAX_VALUE;
      } else {
        final List<String> words = NameUtil.nameToWordsLowerCase(name);
        final List<String> wordsNoDigits = NameUtil.nameToWordsLowerCase(truncDigits(name));
        int max1 = calcMatch(words, 0, expectedInfos);
        max1 = calcMatch(wordsNoDigits, max1, expectedInfos);
        res = max1;
      }
    }

    return res;
  }

  static String truncDigits(String name){
    int count = name.length() - 1;
    while (count >= 0) {
      char c = name.charAt(count);
      if (!Character.isDigit(c)) break;
      count--;
    }
    return name.substring(0, count + 1);
  }

  static int calcMatch(final List<String> words, int max, ExpectedTypeInfo[] myExpectedInfos) {
    for (ExpectedTypeInfo myExpectedInfo : myExpectedInfos) {
      String expectedName = ((ExpectedTypeInfoImpl)myExpectedInfo).expectedName;
      if (expectedName == null) continue;
      max = calcMatch(expectedName, words, max);
      max = calcMatch(truncDigits(expectedName), words, max);
    }
    return max;
  }

  static int calcMatch(final String expectedName, final List<String> words, int max) {
    if (expectedName == null) return max;

    String[] expectedWords = NameUtil.nameToWords(expectedName);
    int limit = Math.min(words.size(), expectedWords.length);
    for (int i = 0; i < limit; i++) {
      String word = words.get(words.size() - i - 1);
      String expectedWord = expectedWords[expectedWords.length - i - 1];
      if (word.equalsIgnoreCase(expectedWord)) {
        max = Math.max(max, i + 1);
      }
      else {
        break;
      }
    }
    return max;
  }

  @Nullable
  static String getLookupObjectName(Object o) {
    if (o instanceof PsiVariable) {
      final PsiVariable variable = (PsiVariable)o;
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(variable.getProject());
      VariableKind variableKind = codeStyleManager.getVariableKind(variable);
      return codeStyleManager.variableNameToPropertyName(variable.getName(), variableKind);
    }
    if (o instanceof PsiMethod) {
      return ((PsiMethod)o).getName();
    }
    return null;
  }

  @Nullable
  public static PsiType getLookupElementType(final LookupElement element) {
    TypedLookupItem typed = typedFrom(element);
    if (typed != null) {
      return typed.getType();
    }

    final PsiType qualifierType = getPsiType(element.getObject());
    final LookupItem lookupItem = LookupItem.from(element);
    if (lookupItem != null) {
      final Object o = lookupItem.getAttribute(LookupItem.TYPE);
      if (o instanceof PsiType) {
        return (PsiType)o;
      }

      final PsiSubstitutor substitutor = (PsiSubstitutor)lookupItem.getAttribute(LookupItem.SUBSTITUTOR);
      if (substitutor != null) {
        return substitutor.substitute(qualifierType);
      }
    }
    return qualifierType;
  }

  public static @Nullable TypedLookupItem typedFrom(LookupElement element) {
    TypedLookupItem typed = null;
    if (element instanceof TypedLookupItem) typed = (TypedLookupItem)element;
    else if (element instanceof LookupElementDecorator) {
      element = ((LookupElementDecorator)element).getDelegate();
      if (element instanceof TypedLookupItem) typed = (TypedLookupItem)element;
    }
    return typed;
  }

  @Nullable
  public static PsiType getQualifiedMemberReferenceType(@Nullable PsiType qualifierType, @NotNull final PsiMember member) {
    final ClassCandidateInfo info = TypeConversionUtil.splitType(qualifierType, member);
    if (info == null) {
      return null;
    }

    final PsiClass element = info.getElement();
    assert element != null;

    final Ref<PsiSubstitutor> subst = Ref.create(PsiSubstitutor.EMPTY);
    class MyProcessor extends BaseScopeProcessor implements NameHint, ElementClassHint {
      public boolean execute(PsiElement element, ResolveState state) {
        if (element == member) {
          subst.set(state.get(PsiSubstitutor.KEY));
        }
        return true;
      }

      public String getName(ResolveState state) {
        return member.getName();
      }

      public boolean shouldProcess(DeclaractionKind kind) {
        return member instanceof PsiEnumConstant ? kind == DeclaractionKind.ENUM_CONST :
               member instanceof PsiField ? kind == DeclaractionKind.FIELD :
               kind == DeclaractionKind.METHOD;
      }

      @Override
      public <T> T getHint(Key<T> hintKey) {
        return hintKey == NameHint.KEY || hintKey == ElementClassHint.KEY ? (T)this : null;
      }
    }

    element.processDeclarations(new MyProcessor(), ResolveState.initial().put(PsiSubstitutor.KEY, info.getSubstitutor()), null, member);

    PsiType rawType = member instanceof PsiField ? ((PsiField) member).getType() : ((PsiMethod) member).getReturnType();
    return subst.get().substitute(rawType);
  }

  public static Set<LookupElement> processJavaReference(PsiElement element, PsiJavaReference javaReference, ElementFilter elementFilter,
                                                        final boolean checkAccess, @Nullable final PrefixMatcher matcher, CompletionParameters parameters) {
    final THashSet<LookupElement> set = new THashSet<LookupElement>();
    final Condition<String> nameCondition = matcher == null ? null : new Condition<String>() {
      public boolean value(String s) {
        return matcher.prefixMatches(s);
      }
    };
    final JavaCompletionProcessor processor = new JavaCompletionProcessor(element, elementFilter, checkAccess, nameCondition);
    javaReference.processVariants(processor);
    final Collection<CompletionElement> plainResults = processor.getResults();

    final PsiType qualifierType = processor.getQualifierType();
    PsiType castedQualifierType = addQualifierCastingVariants(javaReference, processor, set, parameters);

    boolean mayHighlight = qualifierType != null && (castedQualifierType == null || !qualifierType.isAssignableFrom(castedQualifierType));

    for (CompletionElement completionElement : plainResults) {
      LookupElement item = createLookupElement(completionElement, qualifierType);
      if (item != null) {
        set.add(mayHighlight ? highlightIfNeeded(qualifierType, item) : item);
      }
    }

    JavaGlobalMemberNameCompletionContributor.completeStaticMembers(element).processMethodsOfRegisteredClasses(matcher, new Consumer<LookupElement>() {
      @Override
      public void consume(LookupElement element) {
        set.add(element);
      }
    });

    return set;
  }

  @Nullable
  private static PsiType addQualifierCastingVariants(PsiJavaReference javaReference, JavaCompletionProcessor processor, THashSet<LookupElement> set, CompletionParameters parameters) {
    if (javaReference instanceof PsiReferenceExpression) {
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)javaReference;
      final PsiExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier != null) {
        final Project project = qualifier.getProject();
        final PairFunction<PsiExpression, CompletionParameters, PsiType> evaluator = refExpr.getContainingFile().getCopyableUserData(DYNAMIC_TYPE_EVALUATOR);
        PsiType type = null;
        if (evaluator != null) {
          type = evaluator.fun(qualifier, parameters);
        }
        if (type == null) {
          type = GuessManager.getInstance(project).getControlFlowExpressionType(qualifier);
        }
        if (type != null) {
          processor.clear();

          return addQualifierCastingVariants(processor, refExpr, type, set);
        }
      }
    }
    return null;
  }

  private static PsiType addQualifierCastingVariants(JavaCompletionProcessor processor, PsiReferenceExpression refExpr,
                                                     PsiType castTo,
                                                     THashSet<LookupElement> set) {
    Project project = refExpr.getProject();

    PsiExpression qualifier = refExpr.getQualifierExpression();
    assert qualifier != null;
    final String newText = "((" + castTo.getCanonicalText() + ") " + qualifier.getText() + ")." + refExpr.getReferenceName();
    final PsiExpression newRef = JavaPsiFacade.getElementFactory(project).createExpressionFromText(newText, refExpr);
    ((PsiReferenceExpression)newRef).processVariants(processor);

    final LookupElement castItem = PsiTypeLookupItem.createLookupItem(castTo, refExpr);

    for (CompletionElement completionElement : processor.getResults()) {
      final LookupElement item = createLookupElement(completionElement, castTo);
      if (item != null) {
        set.add(highlightIfNeeded(castTo, castQualifier(project, item, castItem)));
      }
    }
    return castTo;
  }

  private static LookupElementDecorator<LookupElement> castQualifier(final Project project, LookupElement item, final LookupElement to) {
    return LookupElementDecorator.withInsertHandler(item, new InsertHandlerDecorator<LookupElement>() {
      public void handleInsert(InsertionContext context, LookupElementDecorator<LookupElement> item) {
        final Document document = context.getEditor().getDocument();
        PsiDocumentManager.getInstance(project).commitDocument(document);
        final PsiFile file = context.getFile();
        final PsiReferenceExpression ref =
          PsiTreeUtil.findElementOfClassAtOffset(file, context.getStartOffset(), PsiReferenceExpression.class, false);
        if (ref != null) {
          final PsiElement qualifier = ref.getQualifier();
          if (qualifier != null) {
            final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);

            final String parenSpace = settings.SPACE_WITHIN_PARENTHESES ? " " : "";
            document.insertString(qualifier.getTextRange().getEndOffset(), parenSpace + ")");

            final String spaceWithin = settings.SPACE_WITHIN_CAST_PARENTHESES ? " " : "";
            final String prefix = "(" + parenSpace + "(" + spaceWithin;
            final String spaceAfter = settings.SPACE_AFTER_TYPE_CAST ? " " : "";
            final int exprStart = qualifier.getTextRange().getStartOffset();
            document.insertString(exprStart, prefix + spaceWithin + ")" + spaceAfter);

            CompletionUtil.emulateInsertion(context, exprStart + prefix.length(), to);
          }
        }

        item.getDelegate().handleInsert(context);
      }
    });
  }

  private static LookupElement highlightIfNeeded(@NotNull PsiType qualifierType, @NotNull LookupElement item) {
    Object o = item.getObject();
    if (qualifierType instanceof PsiArrayType) {
      if (o instanceof PsiField || o instanceof PsiMethod) { //length and clone()
        PsiElement parent = ((PsiElement)o).getParent();
        if (parent instanceof PsiClass && parent.getContainingFile().getVirtualFile() == null) { //yes, they're a bit dummy
          return highlight(item);
        }
      }
    }
    else if (qualifierType instanceof PsiClassType) {
      PsiClass qualifierClass = ((PsiClassType)qualifierType).resolve();
      if (o instanceof PsiField || o instanceof PsiMethod || o instanceof PsiClass) {
        PsiElement parent = ((PsiElement)o).getParent();
        if (parent != null && parent.equals(qualifierClass)) {
          return highlight(item);
        }
      }
    }
    return item;
  }

  private static LookupElementDecorator<LookupElement> highlight(LookupElement decorator) {
    return LookupElementDecorator.withRenderer(decorator, new LookupElementRenderer<LookupElementDecorator<LookupElement>>() {
      @Override
      public void renderElement(LookupElementDecorator<LookupElement> element, LookupElementPresentation presentation) {
        element.getDelegate().renderElement(presentation);
        presentation.setItemTextBold(true);
      }
    });
  }

  private static LookupItem<?> createLookupElement(CompletionElement completionElement, PsiType qualifierType) {
    Object completion = completionElement.getElement();
    assert !(completion instanceof LookupElement);


    LookupElement _ret = LookupItemUtil.objectToLookupItem(completion);
    if (_ret == null || !(_ret instanceof LookupItem)) return null;

    LookupItem<?> ret = (LookupItem<?>)_ret;
    final PsiSubstitutor substitutor = completionElement.getSubstitutor();
    if (substitutor != null) {
      ret.setAttribute(LookupItem.SUBSTITUTOR, substitutor);
    }
    ret.putUserData(QUALIFIER_TYPE_ATTR, qualifierType);

    return ret;
  }

  public static boolean hasAccessibleConstructor(PsiType type) {
    if (type instanceof PsiArrayType) return true;

    final PsiClass psiClass = PsiUtil.resolveClassInType(type);
    if (psiClass == null) return false;

    if (!(psiClass instanceof PsiCompiledElement)) return true;

    final PsiMethod[] methods = psiClass.getConstructors();
    if (methods.length == 0) return true;

    for (final PsiMethod method : methods) {
      if (!method.hasModifierProperty(PsiModifier.PRIVATE)) return true;
    }
    return false;
  }

  public static LookupItem qualify(final LookupItem ret) {
    if (!(ret instanceof JavaMethodCallElement)) {
      final PsiMember completionElement = (PsiMember)ret.getObject();
      final PsiClass containingClass = completionElement.getContainingClass();
      if (containingClass != null) {
        final String className = containingClass.getName();
        ret.setLookupString(className + "." + ret.getLookupString());
      }
    }
    return ret.forceQualify();
  }

  public static LookupItem setShowFQN(final LookupItem ret) {
    final PsiClass psiClass = (PsiClass)ret.getObject();
    @NonNls String packageName = PsiFormatUtil.getPackageDisplayName(psiClass);

    final String tailText = (String)ret.getAttribute(LookupItem.TAIL_TEXT_ATTR);
    ret.setAttribute(LookupItem.TAIL_TEXT_ATTR, StringUtil.notNullize(tailText) + " (" + packageName + ")");
    ret.setAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR, "");
    return ret;
  }

  @Nullable
  static PsiElement getQualifier(final PsiElement element) {
    return element instanceof PsiJavaCodeReferenceElement ? ((PsiJavaCodeReferenceElement)element).getQualifier() : null;
  }

  public static boolean containsMethodCalls(@Nullable final PsiElement qualifier) {
    if (qualifier == null) return false;
    if (qualifier instanceof PsiMethodCallExpression) return true;
    return containsMethodCalls(getQualifier(qualifier));
  }

  @Nullable
  static ElementFilter recursionFilter(PsiElement element) {
    if (PsiJavaPatterns.psiElement().afterLeaf(PsiKeyword.RETURN).inside(PsiReturnStatement.class).accepts(element)) {
      return new ExcludeDeclaredFilter(ElementClassFilter.METHOD);
    }

    if (PsiJavaPatterns.psiElement().inside(
        PsiJavaPatterns.or(
            PsiJavaPatterns.psiElement(PsiAssignmentExpression.class),
            PsiJavaPatterns.psiElement(PsiVariable.class))).
        andNot(PsiJavaPatterns.psiElement().afterLeaf(".")).accepts(element)) {
      return new AndFilter(new ExcludeSillyAssignment(),
                                                   new ExcludeDeclaredFilter(new ClassFilter(PsiVariable.class)));
    }
    return null;
  }

  public static int insertClassReference(@NotNull PsiClass psiClass, @NotNull PsiFile file, int offset) {
    return insertClassReference(psiClass, file, offset, offset);
  }

  public static int insertClassReference(PsiClass psiClass, PsiFile file, int startOffset, int endOffset) {
    PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();
    if (!psiClass.isValid()) {
      return startOffset;
    }

    SmartPsiElementPointer<PsiClass> pointer = SmartPointerManager.getInstance(file.getProject()).createSmartPsiElementPointer(psiClass);
    LOG.assertTrue(CommandProcessor.getInstance().getCurrentCommand() != null);
    LOG.assertTrue(
      ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().getCurrentWriteAction(null) != null);

    final PsiManager manager = file.getManager();

    final Document document = FileDocumentManager.getInstance().getDocument(file.getViewProvider().getVirtualFile());

    final PsiReference reference = file.findReferenceAt(startOffset);
    if (reference != null) {
      final PsiElement resolved = reference.resolve();
      if (resolved instanceof PsiClass) {
        if (((PsiClass)resolved).getQualifiedName() == null || manager.areElementsEquivalent(psiClass, resolved)) {
          return startOffset;
        }
      }
    }

    String name = psiClass.getName();
    document.replaceString(startOffset, endOffset, name);

    final RangeMarker toDelete = insertSpace(startOffset + name.length(), document);

    PsiDocumentManager.getInstance(manager.getProject()).commitAllDocuments();

    int newStartOffset = startOffset;
    PsiElement element = file.findElementAt(startOffset);
    if (element instanceof PsiIdentifier) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement && !((PsiJavaCodeReferenceElement)parent).isQualified() && !(parent.getParent() instanceof PsiPackageStatement)) {
        PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)parent;

        if (!psiClass.getManager().areElementsEquivalent(psiClass, resolveReference(ref))) {
          final PsiElement pointerElement = pointer.getElement();
          if (pointerElement instanceof PsiClass) {
            PsiElement newElement;
            if (!(ref instanceof PsiImportStaticReferenceElement)) {
              newElement = ref.bindToElement(pointerElement);
            }
            else {
              newElement = ((PsiImportStaticReferenceElement)ref).bindToTargetClass((PsiClass)pointerElement);
            }
            RangeMarker marker = document.createRangeMarker(newElement.getTextRange());
            CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(newElement);
            newStartOffset = marker.getStartOffset();
          }
        }
      }
    }

    if (toDelete.isValid()) {
      document.deleteString(toDelete.getStartOffset(), toDelete.getEndOffset());
    }

    return newStartOffset;
  }

  @Nullable
  static PsiElement resolveReference(final PsiReference psiReference) {
    if (psiReference instanceof PsiPolyVariantReference) {
      final ResolveResult[] results = ((PsiPolyVariantReference)psiReference).multiResolve(true);
      if (results.length == 1) return results[0].getElement();
    }
    return psiReference.resolve();
  }

  public static RangeMarker insertSpace(final int endOffset, final Document document) {
    final CharSequence chars = document.getCharsSequence();
    final int length = chars.length();
    final RangeMarker toDelete;
    if (endOffset < length && Character.isJavaIdentifierPart(chars.charAt(endOffset))){
      document.insertString(endOffset, " ");
      toDelete = document.createRangeMarker(endOffset, endOffset + 1);
    } else if (endOffset >= length) {
      toDelete = document.createRangeMarker(length, length);
    }
    else {
      toDelete = document.createRangeMarker(endOffset, endOffset);
    }
    toDelete.setGreedyToLeft(true);
    toDelete.setGreedyToRight(true);
    return toDelete;
  }

  public static void insertParentheses(final InsertionContext context, final LookupElement item, boolean overloadsMatter, boolean hasParams) {
    final Editor editor = context.getEditor();
    final TailType tailType = getTailType(item, context);
    final PsiFile file = context.getFile();

    context.setAddCompletionChar(false);


    final boolean needLeftParenth = isToInsertParenth(file.findElementAt(context.getStartOffset()));
    final boolean needRightParenth = shouldInsertRParenth(context.getCompletionChar(), tailType, hasParams);

    if (needLeftParenth) {
      final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(context.getProject());
      ParenthesesInsertHandler.getInstance(hasParams,
                                           styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES,
                                           styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES && hasParams,
                                           needRightParenth,
                                           styleSettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE
      ).handleInsert(context, item);
    }

    if (needLeftParenth && hasParams) {
      // Invoke parameters popup
      AutoPopupController.getInstance(file.getProject()).autoPopupParameterInfo(editor, overloadsMatter ? null : (PsiElement)item.getObject());
    }
    if (tailType == TailType.SMART_COMPLETION || needLeftParenth && needRightParenth) {
      tailType.processTail(editor, context.getTailOffset());
    }
  }

  public static boolean shouldInsertRParenth(char completionChar, TailType tailType, boolean hasParams) {
    if (tailType == TailType.SMART_COMPLETION) {
      return false;
    }

    if (completionChar == '(' && !hasParams) {
      //it's highly probable that the user will type ')' next and it may not be overwritten if the flag is off
      return CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET;
    }

    return true;
  }

  @NotNull
  public static TailType getTailType(final LookupElement item, InsertionContext context) {
    final char completionChar = context.getCompletionChar();
    if (completionChar == '!') return item instanceof LookupItem ? ((LookupItem)item).getTailType() : TailType.NONE;
    if (completionChar == '(') {
      final Object o = item.getObject();
      if (o instanceof PsiMethod) {
        final PsiMethod psiMethod = (PsiMethod)o;
        return psiMethod.getParameterList().getParameters().length > 0 || psiMethod.getReturnType() != PsiType.VOID
               ? TailType.NONE : TailType.SEMICOLON;
      } else if (o instanceof PsiClass) { // it may be a constructor
        return TailType.NONE;
      }
    }
    if (completionChar == Lookup.COMPLETE_STATEMENT_SELECT_CHAR) return TailType.SMART_COMPLETION;
    if (!context.shouldAddCompletionChar()) {
      return TailType.NONE;
    }

    return LookupItem.handleCompletionChar(context.getEditor(), item, completionChar);
  }

  public static boolean isToInsertParenth(PsiElement place){
    if (place == null) return true;
    return !(place.getParent() instanceof PsiImportStaticReferenceElement);
  }

  //need to shorten references in type argument list
  public static void shortenReference(final PsiFile file, final int offset) throws IncorrectOperationException {
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(file.getProject());
    final Document document = manager.getDocument(file);
    assert document != null;
    manager.commitDocument(document);
    final PsiReference ref = file.findReferenceAt(offset);
    if (ref instanceof PsiJavaCodeReferenceElement) {
      JavaCodeStyleManager.getInstance(file.getProject()).shortenClassReferences((PsiJavaCodeReferenceElement)ref);
    }
  }
}
