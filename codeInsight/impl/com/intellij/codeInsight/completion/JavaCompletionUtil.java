package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.NullableLazyKey;
import com.intellij.openapi.util.text.StringUtil;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaCompletionUtil {
  static final Key<SmartPsiElementPointer> QUALIFIER_TYPE_ATTR = Key.create("qualifierType"); // SmartPsiElementPointer to PsiType of "qualifier"
  static final NotNullLazyValue<CompletionData> ourJavaCompletionData = new NotNullLazyValue<CompletionData>() {
    @NotNull
    protected CompletionData compute() {
      return new JavaCompletionData();
    }
  };
  static final NotNullLazyValue<CompletionData> ourJava15CompletionData = new NotNullLazyValue<CompletionData>() {
    @NotNull
    protected CompletionData compute() {
      return new Java15CompletionData();
    }
  };
  static final NotNullLazyValue<CompletionData> ourJavaDocCompletionData = new NotNullLazyValue<CompletionData>() {
    @NotNull
    protected CompletionData compute() {
      return new JavaDocCompletionData();
    }
  };
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
      return getExpectedTypes(location.getCompletionParameters());
    }
  });
  private static final PsiElementPattern.Capture<PsiElement> LEFT_PAREN = psiElement(JavaTokenType.LPARENTH).andOr(psiElement().withParent(
      PsiExpressionList.class), psiElement().afterLeaf(".", PsiKeyword.NEW));

  @Nullable
  public static ExpectedTypeInfo[] getExpectedTypes(final CompletionParameters parameters) {
    final PsiExpression expr = PsiTreeUtil.getContextOfType(parameters.getPosition(), PsiExpression.class, true);
    if (expr != null) {
      final ExpectedTypeInfo[] expectedInfos = ExpectedTypesProvider.getInstance(parameters.getPosition().getProject()).getExpectedTypes(expr, true);
      if(expectedInfos != null){
        final Map<PsiType, ExpectedTypeInfo> map = new HashMap<PsiType, ExpectedTypeInfo>(expectedInfos.length);
        for (final ExpectedTypeInfo expectedInfo : expectedInfos) {
          if (!map.containsKey(expectedInfo.getType())) {
            map.put(expectedInfo.getType(), expectedInfo);
          }
        }
        return map.values().toArray(new ExpectedTypeInfo[map.size()]);
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
  public static final Key<String> QUALIFIER_PREFIX_ATTRIBUTE = Key.create("qualifierPrefix");

  public static void completeLocalVariableName(Set<LookupItem> set, PrefixMatcher matcher, PsiVariable var){
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(var.getProject());
    final VariableKind variableKind = codeStyleManager.getVariableKind(var);

    String propertyName = null;
    if (variableKind == VariableKind.PARAMETER) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(var, PsiMethod.class);
      propertyName = PropertyUtil.getPropertyName(method);
    }

    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(variableKind, propertyName, null, var.getType());
    final String[] suggestedNames = suggestedNameInfo.names;
    tunePreferencePolicy(LookupItemUtil.addLookupItems(set, suggestedNames, matcher), suggestedNameInfo);

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
      JavaStatisticsManager.getNameSuggestions(var.getType(), JavaStatisticsManager.getContext(var), matcher.getPrefix());
    tunePreferencePolicy(LookupItemUtil.addLookupItems(set, nameSuggestions, matcher), suggestedNameInfo);
  }

  public static void completeFieldName(Set<LookupItem> set, PsiVariable var, final PrefixMatcher matcher){
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");

    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(var.getProject());
    final VariableKind variableKind = JavaCodeStyleManager.getInstance(var.getProject()).getVariableKind(var);

    final String prefix = matcher.getPrefix();
    if (var.getType() == PsiType.VOID ||
        prefix.startsWith(IS_PREFIX) ||
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

  public static void completeMethodName(Set<LookupItem> set, PsiElement element, final PrefixMatcher matcher){
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

    LookupItemUtil.addLookupItems(set, getUnresolvedReferences(element.getParent(), true), matcher);
    if(!((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.PRIVATE)){
      LookupItemUtil.addLookupItems(set, getOverides((PsiClass)element.getParent(), PsiUtil.getTypeByPsiElement(element)),
                                    matcher);
      LookupItemUtil.addLookupItems(set, getImplements((PsiClass)element.getParent(), PsiUtil.getTypeByPsiElement(element)),
                                    matcher);
    }
    LookupItemUtil.addLookupItems(set, getPropertiesHandlersNames(
      (PsiClass)element.getParent(),
      ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC),
      PsiUtil.getTypeByPsiElement(element), element), matcher);
  }

  public static PsiType getQualifierType(LookupItem item) {
    return (PsiType)item.getAttribute(QUALIFIER_TYPE_ATTR);
  }

  public static void setQualifierType(LookupItem item, PsiType type) {
    item.setAttribute(QUALIFIER_TYPE_ATTR, type);
  }

  static void highlightMembersOfContainer(Set<LookupItem> set) {
    for (final LookupItem item : set) {
      highlightMemberOfContainer(item);
    }
  }

  public static void highlightMemberOfContainer(final LookupItem item) {
    Object o = item.getObject();
    PsiType qualifierType = getQualifierType(item);
    if (qualifierType == null) return;
    if (qualifierType instanceof PsiArrayType) {
      if (o instanceof PsiField || o instanceof PsiMethod || o instanceof PsiClass) {
        PsiElement parent = ((PsiElement)o).getParent();
        if (parent instanceof PsiClass && parent.getContainingFile().getVirtualFile() == null) { //?
          item.setAttribute(LookupItem.HIGHLIGHTED_ATTR, "");
        }
      }
    }
    else if (qualifierType instanceof PsiClassType) {
      PsiClass qualifierClass = ((PsiClassType)qualifierType).resolve();
      if (o instanceof PsiField || o instanceof PsiMethod || o instanceof PsiClass) {
        PsiElement parent = ((PsiElement)o).getParent();
        if (parent != null && parent.equals(qualifierClass)) {
          item.setAttribute(LookupItem.HIGHLIGHTED_ATTR, "");
        }
      }
    }
  }

  public static LookupItemPreferencePolicy completeVariableNameForRefactoring(Project project, Set<LookupItem> set, String prefix, PsiType varType, VariableKind varKind) {
    return completeVariableNameForRefactoring(project, set, new CamelHumpMatcher(prefix), varType, varKind);
  }

  public static LookupItemPreferencePolicy completeVariableNameForRefactoring(Project project, Set<LookupItem> set, PrefixMatcher matcher, PsiType varType, VariableKind varKind) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(varKind, null, null, varType);
    final String[] suggestedNames = suggestedNameInfo.names;
    tunePreferencePolicy(LookupItemUtil.addLookupItems(set, suggestedNames, matcher), suggestedNameInfo);

    if (set.isEmpty() && PsiType.VOID != varType) {
      // use suggested names as suffixes
      final String requiredSuffix = codeStyleManager.getSuffixByVariableKind(varKind);
      final String prefix = matcher.getPrefix();
      final boolean isMethodPrefix = prefix.startsWith(IS_PREFIX) || prefix.startsWith(GET_PREFIX) || prefix.startsWith(SET_PREFIX);
      if (varKind != VariableKind.STATIC_FINAL_FIELD || isMethodPrefix) {
        for (int i = 0; i < suggestedNames.length; i++) {
          suggestedNames[i] = codeStyleManager.variableNameToPropertyName(suggestedNames[i], varKind);
        }
      }

      suggestedNameInfo = new SuggestedNameInfo(getOverlappedNameVersions(prefix, suggestedNames, requiredSuffix)) {
        public void nameChoosen(String name) {
        }
      };

      tunePreferencePolicy(LookupItemUtil.addLookupItems(set, suggestedNameInfo.names, matcher), suggestedNameInfo);
    }
    return new NamePreferencePolicy(suggestedNameInfo);
  }

  public static void tunePreferencePolicy(final List<LookupItem> list, final SuggestedNameInfo suggestedNameInfo) {
    final InsertHandler insertHandler = new InsertHandler() {
      public void handleInsert(final InsertionContext context, final LookupElement item) {
        suggestedNameInfo.nameChoosen(item.getLookupString());
      }
    };

    for (int i = 0; i < list.size(); i++) {
      LookupItem item = list.get(i);
      item.setPriority(list.size() - i).setInsertHandler(insertHandler);
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
    return newSuggestions.toArray(new String[newSuggestions.size()]);
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
      if (typeByPsiElement == PsiUtil.getTypeByPsiElement(element) && element instanceof PsiNamedElement) {
        overides
          .add(((PsiNamedElement)element).getName());
      }
    }
    return overides.toArray(new String[overides.size()]);
  }

  public static String[] getImplements(final PsiClass parent, final PsiType typeByPsiElement) {
    final List<String> overides = new ArrayList<String>();
    final Collection<CandidateInfo> methodsToOverrideImplement = OverrideImplementUtil.getMethodsToOverrideImplement(parent, false);
    for (final CandidateInfo candidateInfo : methodsToOverrideImplement) {
      final PsiElement element = candidateInfo.getElement();
      if (typeByPsiElement == PsiUtil.getTypeByPsiElement(element) && element instanceof PsiNamedElement) {
        overides
          .add(((PsiNamedElement)element).getName());
      }
    }
    return overides.toArray(new String[overides.size()]);
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
          final PsiMethod getter = PropertyUtil.generateGetterPrototype(field);
          if (getter.getReturnType().equals(varType) && psiClass.findMethodBySignature(getter, true) == null) {
            propertyHandlers.add(getter.getName());
          }

          final PsiMethod setter = PropertyUtil.generateSetterPrototype(field);
          if (setter.getReturnType().equals(varType) && psiClass.findMethodBySignature(setter, true) == null) {
            propertyHandlers.add(setter.getName());
          }
        }
        result = propertyHandlers.toArray(new String[propertyHandlers.size()]);
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
    boolean isExcluded = false;
    for (String packages : cis.EXCLUDED_PACKAGES) {
      if (name.startsWith(packages)) {
        isExcluded = true;
      }
    }
    return isExcluded;
  }

  public static boolean isCompletionOfAnnotationMethod(final PsiElement method, final PsiElement place) {
    return method instanceof PsiAnnotationMethod &&
      (place instanceof PsiIdentifier &&
        (place.getParent() instanceof PsiNameValuePair ||
         place.getParent().getParent() instanceof PsiNameValuePair ||
         // @AAA(|A.class)
         ( place.getParent().getParent().getParent() instanceof PsiClassObjectAccessExpression &&
           place.getParent().getParent().getParent().getParent() instanceof PsiNameValuePair
         )
        )
     );
  }

  @SuppressWarnings({"unchecked"})
  @NotNull
  public static <T extends PsiType> T originalize(@NotNull T type) {
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

        return new PsiImmediateClassType(CompletionUtil.getOriginalElement(psiClass), originalize(substitutor));
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

  public static PsiSubstitutor originalize(@Nullable final PsiSubstitutor substitutor) {
    if (substitutor == null) return null;

    PsiSubstitutor originalSubstitutor = PsiSubstitutor.EMPTY;
    for (final Map.Entry<PsiTypeParameter, PsiType> entry : substitutor.getSubstitutionMap().entrySet()) {
      final PsiType value = entry.getValue();
      originalSubstitutor = originalSubstitutor.put(CompletionUtil.getOriginalElement(entry.getKey()), value == null ? null : originalize(value));
    }
    return originalSubstitutor;
  }

  public static String[] getUnresolvedReferences(final PsiElement parentOfType, final boolean referenceOnMethod) {
    if (parentOfType != null && parentOfType.getTextLength() > MAX_SCOPE_SIZE_TO_SEARCH_UNRESOLVED) return ArrayUtil.EMPTY_STRING_ARRAY;
    final List<String> unresolvedRefs = new ArrayList<String>();

    if (parentOfType != null) {
      parentOfType.accept(new JavaRecursiveElementVisitor() {
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
    return unresolvedRefs.toArray(new String[unresolvedRefs.size()]);
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
    if (!(item instanceof LookupItem)) return null;

    List<PsiMethod> allMethods = ((LookupItem<?>)item).getAttribute(ALL_METHODS_ATTRIBUTE);
    if (allMethods != null) return allMethods;
    if (item.getObject() instanceof PsiElement) return Arrays.asList((PsiElement)item.getObject());
    return null;
  }

  @Nullable
  public static PsiType getPsiType(final Object o) {
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

  public static int getNameEndMatchingDegree(final Object object, final String name, ExpectedTypeInfo[] expectedInfos, String prefix) {
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

    if (object instanceof String) res = 1;
    else if (object instanceof PsiKeyword) res = -1;
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
}
