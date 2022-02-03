// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.*;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiClass;
import static com.intellij.patterns.PsiJavaPatterns.psiField;
import static com.intellij.patterns.StandardPatterns.or;

/**
 * @author peter
 */
public final class JavaMemberNameCompletionContributor extends CompletionContributor implements DumbAware {
  public static final ElementPattern<PsiElement> INSIDE_TYPE_PARAMS_PATTERN = psiElement().
    afterLeaf(psiElement().withText("?").andOr(
      psiElement().afterLeaf("<", ","),
      psiElement().afterSiblingSkipping(psiElement().whitespaceCommentEmptyOrError(), psiElement(PsiAnnotation.class))));

  static final int MAX_SCOPE_SIZE_TO_SEARCH_UNRESOLVED = 50000;

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.BASIC && parameters.getCompletionType() != CompletionType.SMART) {
      return;
    }

    if (parameters.getInvocationCount() == 0 && TemplateManagerImpl.getTemplateState(parameters.getEditor()) != null) {
      return;
    }

    PsiElement position = parameters.getPosition();
    final Set<LookupElement> lookupSet = new HashSet<>();
    if (psiElement(PsiIdentifier.class).andNot(INSIDE_TYPE_PARAMS_PATTERN).withParent(
      or(psiElement(PsiLocalVariable.class), psiElement(PsiParameter.class))).accepts(position)) {
      completeLocalVariableName(lookupSet, result.getPrefixMatcher(), (PsiVariable)parameters.getPosition().getParent(),
                                parameters.getInvocationCount() >= 1);
      for (final LookupElement item : lookupSet) {
        if (item instanceof LookupItem) {
          ((LookupItem<?>)item).setAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE);
        }
      }
    }

    if (psiElement(PsiIdentifier.class).withParent(PsiField.class).andNot(INSIDE_TYPE_PARAMS_PATTERN).accepts(position)) {
      final PsiField variable = (PsiField)parameters.getPosition().getParent();
      completeMethodName(lookupSet, variable, result.getPrefixMatcher());
      completeFieldName(lookupSet, variable, result.getPrefixMatcher(), parameters.getInvocationCount() >= 1);
    }

    if (psiElement(PsiIdentifier.class).withParent(PsiRecordComponent.class).andNot(INSIDE_TYPE_PARAMS_PATTERN).accepts(position)) {
      PsiRecordComponent component = (PsiRecordComponent)parameters.getPosition().getParent();
      completeComponentName(lookupSet, component, result.getPrefixMatcher());
    }

      if (PsiJavaPatterns.psiElement().nameIdentifierOf(PsiJavaPatterns.psiMethod().withParent(PsiClass.class)).accepts(position)) {
      completeMethodName(lookupSet, parameters.getPosition().getParent(), result.getPrefixMatcher());
    }

    for (final LookupElement item : lookupSet) {
      result.addElement(item);
    }
  }

  private static void completeLocalVariableName(Set<LookupElement> set, PrefixMatcher matcher, PsiVariable var, boolean includeOverlapped) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");
    Project project = var.getProject();
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    final VariableKind variableKind = codeStyleManager.getVariableKind(var);

    String propertyName = null;
    if (variableKind == VariableKind.PARAMETER) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(var, PsiMethod.class);
      if (method != null) {
        propertyName = PropertyUtilBase.getPropertyName(method);
      }
      if (method != null && method.getName().startsWith("with")) {
        propertyName = StringUtil.decapitalize(method.getName().substring(4));
      }
    }

    PsiType type = var.getType();
    if (type instanceof PsiClassType &&
        ((PsiClassType)type).resolve() == null &&
        seemsMistypedKeyword(((PsiClassType)type).getClassName())) {
      return;
    }

    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(variableKind, propertyName, null, type, StringUtil.isEmpty(matcher.getPrefix()));
    suggestedNameInfo = codeStyleManager.suggestUniqueVariableName(suggestedNameInfo, var, false);
    final String[] suggestedNames = suggestedNameInfo.names;
    addLookupItems(set, suggestedNameInfo, matcher, project, suggestedNames);
    if (!hasStartMatches(set, matcher)) {
      if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) && matcher.prefixMatches("object")) {
        set.add(withInsertHandler(suggestedNameInfo, LookupElementBuilder.create("object")));
      }
      if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING) && matcher.prefixMatches("string")) {
        set.add(withInsertHandler(suggestedNameInfo, LookupElementBuilder.create("string")));
      }
    }

    if (!hasStartMatches(set, matcher) && includeOverlapped) {
      addLookupItems(set, null, matcher, project, getOverlappedNameVersions(matcher.getPrefix(), suggestedNames, ""));
    }
    PsiElement parent = PsiTreeUtil.getParentOfType(var, PsiCodeBlock.class);
    if(parent == null) parent = PsiTreeUtil.getParentOfType(var, PsiMethod.class, PsiLambdaExpression.class);
    addLookupItems(set, suggestedNameInfo, matcher, project, getUnresolvedReferences(parent, false, matcher));
    if (var instanceof PsiParameter && parent instanceof PsiMethod) {
      addSuggestionsInspiredByFieldNames(set, matcher, var, project, codeStyleManager);
      PsiDocComment docComment = ((PsiMethod)parent).getDocComment();
      if (docComment != null) {
        addLookupItems(set, null, matcher, project, ArrayUtil.toStringArray(getUnresolvedMethodParamNamesFromJavadoc(docComment)));
      }
    }

    PsiExpression initializer = var.getInitializer();
    if (initializer != null) {
      SuggestedNameInfo initializerSuggestions = CommonJavaRefactoringUtil.getSuggestedName(type, initializer);
      addLookupItems(set, initializerSuggestions, matcher, project, initializerSuggestions.names);
    }
  }

  @NotNull
  private static List<String> getUnresolvedMethodParamNamesFromJavadoc(@NotNull PsiDocComment docComment) {
    List<String> result = new ArrayList<>();
    for (PsiDocTag tag : docComment.getTags()) {
      if ("param".equals(tag.getName())) {
        PsiDocTagValue value = tag.getValueElement();
        if (value instanceof PsiDocParamRef && !((PsiDocParamRef)value).isTypeParamRef()) {
          ASTNode token = ((PsiDocParamRef)value).getValueToken();
          PsiReference psiReference = value.getReference();
          if (psiReference != null && psiReference.resolve() == null && token != null) {
            result.add(token.getText());
          }
        }
      }
    }
    return result;
  }

  private static boolean seemsMistypedKeyword(String className) {
    return className != null && !StringUtil.isCapitalized(className);
  }

  private static boolean hasStartMatches(PrefixMatcher matcher, Set<String> set) {
    for (String s : set) {
      if (matcher.isStartMatch(s)) {
        return true;
      }
    }
    return false;
  }
  private static boolean hasStartMatches(Set<? extends LookupElement> set, PrefixMatcher matcher) {
    for (LookupElement lookupElement : set) {
      if (matcher.isStartMatch(lookupElement)) {
        return true;
      }
    }
    return false;
  }

  private static void addSuggestionsInspiredByFieldNames(Set<LookupElement> set,
                                                         PrefixMatcher matcher,
                                                         PsiVariable var,
                                                         Project project,
                                                         JavaCodeStyleManager codeStyleManager) {
    PsiClass psiClass = PsiTreeUtil.getParentOfType(var, PsiClass.class);
    if (psiClass == null) {
      return;
    }

    for (PsiField field : psiClass.getFields()) {
      String name = field.getName();
      if (field.getType().isAssignableFrom(var.getType())) {
        String prop = codeStyleManager.variableNameToPropertyName(name, VariableKind.FIELD);
        addLookupItems(set, null, matcher, project, codeStyleManager.propertyNameToVariableName(prop, VariableKind.PARAMETER));
      }
    }
  }

  private static String[] getOverlappedNameVersions(final String prefix, final String[] suggestedNames, String suffix) {
    final List<String> newSuggestions = new ArrayList<>();
    int longestOverlap = 0;

    for (String suggestedName : suggestedNames) {
      if (suggestedName.length() < 3) {
        continue;
      }

      if (StringUtil.startsWithIgnoreCase(suggestedName, prefix)) {
        newSuggestions.add(suggestedName);
        longestOverlap = prefix.length();
      }

      suggestedName = Character.toUpperCase(suggestedName.charAt(0)) + suggestedName.substring(1);
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
    return ArrayUtilRt.toStringArray(newSuggestions);
  }

  private static int getOverlap(final String propertyName, final String prefix) {
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

  private static String[] getUnresolvedReferences(final PsiElement parentOfType, final boolean referenceOnMethod, PrefixMatcher matcher) {
    if (parentOfType != null && parentOfType.getTextLength() > MAX_SCOPE_SIZE_TO_SEARCH_UNRESOLVED) return ArrayUtilRt.EMPTY_STRING_ARRAY;
    final Set<String> unresolvedRefs = new LinkedHashSet<>();

    if (parentOfType != null) {
      parentOfType.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitReferenceExpression(PsiReferenceExpression reference) {
          final PsiElement parent = reference.getParent();
          if (parent instanceof PsiReference || referenceOnMethod != parent instanceof PsiMethodCallExpression) return;

          String refName = reference.getReferenceName();
          if (refName != null && matcher.prefixMatches(refName) && reference.resolve() == null) {
            unresolvedRefs.add(refName);
          }
        }
      });
    }
    return ArrayUtilRt.toStringArray(unresolvedRefs);
  }

  private static void completeComponentName(Set<LookupElement> set, PsiRecordComponent var, final PrefixMatcher matcher) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");

    Project project = var.getProject();
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);

    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, var.getType());
    final String[] suggestedNames = suggestedNameInfo.names;
    addLookupItems(set, suggestedNameInfo, matcher, project, suggestedNames);
  }

  private static void completeFieldName(Set<LookupElement> set, PsiField var, final PrefixMatcher matcher, boolean includeOverlapped) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");

    Project project = var.getProject();
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    final VariableKind variableKind = JavaCodeStyleManager.getInstance(project).getVariableKind(var);

    final String prefix = matcher.getPrefix();
    if (PsiType.VOID.equals(var.getType()) || psiField().inClass(psiClass().isInterface().andNot(psiClass().isAnnotationType())).accepts(var)) {
      completeVariableNameForRefactoring(project, set, matcher, var.getType(), variableKind, includeOverlapped, true);
      return;
    }

    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(variableKind, null, null, var.getType());
    final String[] suggestedNames = suggestedNameInfo.names;
    addLookupItems(set, suggestedNameInfo, matcher, project, suggestedNames);

    if (!hasStartMatches(set, matcher) && includeOverlapped) {
      // use suggested names as suffixes
      final String requiredSuffix = codeStyleManager.getSuffixByVariableKind(variableKind);
      if(variableKind != VariableKind.STATIC_FINAL_FIELD){
        for (int i = 0; i < suggestedNames.length; i++)
          suggestedNames[i] = codeStyleManager.variableNameToPropertyName(suggestedNames[i], variableKind);
      }


      addLookupItems(set, null, matcher, project, getOverlappedNameVersions(prefix, suggestedNames, requiredSuffix));
    }

    addLookupItems(set, suggestedNameInfo, matcher, project, getUnresolvedReferences(var.getParent(), false, matcher));

    PsiExpression initializer = var.getInitializer();
    PsiClass containingClass = var.getContainingClass();
    if (initializer != null && containingClass != null) {
      SuggestedNameInfo initializerSuggestions = JavaSpecialRefactoringProvider.getInstance()
        .suggestFieldName(var.getType(), null, initializer, var.hasModifierProperty(PsiModifier.STATIC), containingClass);
      addLookupItems(set, initializerSuggestions, matcher, project, initializerSuggestions.names);
    }
  }

  public static void completeVariableNameForRefactoring(Project project,
                                                        Set<LookupElement> set,
                                                        PrefixMatcher matcher,
                                                        PsiType varType,
                                                        VariableKind varKind, final boolean includeOverlapped, final boolean methodPrefix) {
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(varKind, null, null, varType);
    final String[] strings = completeVariableNameForRefactoring(codeStyleManager, matcher, varType, varKind, suggestedNameInfo,
                                                                includeOverlapped, methodPrefix);
    addLookupItems(set, suggestedNameInfo, matcher, project, strings);
  }

  public static String[] completeVariableNameForRefactoring(JavaCodeStyleManager codeStyleManager,
                                                            final PrefixMatcher matcher,
                                                            @Nullable final PsiType varType,
                                                            final VariableKind varKind,
                                                            SuggestedNameInfo suggestedNameInfo,
                                                            final boolean includeOverlapped, final boolean methodPrefix) {
    Set<String> result = new LinkedHashSet<>();
    final String[] suggestedNames = suggestedNameInfo.names;
    for (final String suggestedName : suggestedNames) {
      if (matcher.prefixMatches(suggestedName)) {
        result.add(suggestedName);
      }
    }

    if (!hasStartMatches(matcher, result) && !PsiType.VOID.equals(varType) && includeOverlapped) {
      // use suggested names as suffixes
      final String requiredSuffix = codeStyleManager.getSuffixByVariableKind(varKind);
      final String prefix = matcher.getPrefix();
      if (varKind != VariableKind.STATIC_FINAL_FIELD || methodPrefix) {
        for (int i = 0; i < suggestedNames.length; i++) {
          suggestedNames[i] = codeStyleManager.variableNameToPropertyName(suggestedNames[i], varKind);
        }
      }

      ContainerUtil.addAll(result, getOverlappedNameVersions(prefix, suggestedNames, requiredSuffix));


    }
    return ArrayUtilRt.toStringArray(result);
  }

  private static void completeMethodName(Set<LookupElement> set, PsiElement element, final PrefixMatcher matcher){
    if(element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      if (method.isConstructor()) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
          final String name = containingClass.getName();
          if (StringUtil.isNotEmpty(name)) {
            addLookupItems(set, null, matcher, element.getProject(), name);
          }
        }
        return;
      }
    }

    PsiClass ourClassParent = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (ourClassParent == null) return;

    if (ourClassParent.isAnnotationType() && matcher.prefixMatches(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)) {
      set.add(LookupElementBuilder.create(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)
                .withIcon(PlatformIcons.METHOD_ICON)
                .withTailText("()")
                .withInsertHandler(ParenthesesInsertHandler.NO_PARAMETERS));
    }

    if (element instanceof PsiField && ((PsiField)element).hasModifierProperty(PsiModifier.STATIC) &&
        ourClassParent.equals(PsiUtil.resolveClassInClassTypeOnly(((PsiField)element).getType()))) {
      set.add(LookupElementBuilder.create("getInstance")
                                  .withIcon(PlatformIcons.METHOD_ICON)
                                  .withTailText("()")
                                  .withInsertHandler(ParenthesesInsertHandler.WITH_PARAMETERS));

    }
    addLookupItems(set, null, matcher, element.getProject(), getUnresolvedReferences(ourClassParent, true, matcher));

    addLookupItems(set, null, matcher, element.getProject(), getPropertiesHandlersNames(
      ourClassParent,
      ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC),
      PsiUtil.getTypeByPsiElement(element), element));
  }

  private static String[] getPropertiesHandlersNames(final PsiClass psiClass,
                                                    final boolean staticContext,
                                                    final PsiType varType,
                                                    final PsiElement element) {
    final List<String> propertyHandlers = new ArrayList<>();

    for (final PsiField field : psiClass.getFields()) {
      if (field == element) continue;
      if (StringUtil.isEmpty(field.getName())) continue;

      PsiUtilCore.ensureValid(field);
      PsiType fieldType = field.getType();
      PsiUtil.ensureValidType(fieldType);

      final PsiModifierList modifierList = field.getModifierList();
      if (staticContext && (modifierList != null && !modifierList.hasModifierProperty(PsiModifier.STATIC))) continue;

      if (fieldType.equals(varType)) {
        final String getterName = JavaPsiRecordUtil.getComponentForField(field) != null ?
                                  field.getName() : PropertyUtilBase.suggestGetterName(field);
        if ((psiClass.findMethodsByName(getterName, true).length == 0 ||
             psiClass.findMethodBySignature(GenerateMembersUtil.generateGetterPrototype(field), true) == null)) {
          propertyHandlers.add(getterName);
        }
      }

      if (PsiType.VOID.equals(varType)) {
        final String setterName = PropertyUtilBase.suggestSetterName(field);
        if ((psiClass.findMethodsByName(setterName, true).length == 0 ||
             psiClass.findMethodBySignature(GenerateMembersUtil.generateSetterPrototype(field), true) == null)) {
          propertyHandlers.add(setterName);
        }
      }
    }

    return ArrayUtilRt.toStringArray(propertyHandlers);
  }

  private static void addLookupItems(Set<LookupElement> lookupElements, @Nullable final SuggestedNameInfo callback, PrefixMatcher matcher, Project project, String... strings) {
    outer:
    for (int i = 0; i < strings.length; i++) {
      String name = strings[i];
      if (!matcher.prefixMatches(name) || !PsiNameHelper.getInstance(project).isIdentifier(name, LanguageLevel.HIGHEST)) {
        continue;
      }

      for (LookupElement lookupElement : lookupElements) {
        if (lookupElement.getAllLookupStrings().contains(name)) {
          continue outer;
        }
      }

      LookupElement element = PrioritizedLookupElement.withPriority(LookupElementBuilder.create(name).withAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE), -i);
      if (callback != null) {
        element = withInsertHandler(callback, element);
      }
      lookupElements.add(element);
    }
  }

  private static LookupElementDecorator<LookupElement> withInsertHandler(final SuggestedNameInfo callback, LookupElement element) {
    return LookupElementDecorator.withInsertHandler(element, new InsertHandler<>() {
      @Override
      public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElementDecorator<LookupElement> item) {
        TailType tailType = LookupItem.getDefaultTailType(context.getCompletionChar());
        if (tailType != null) {
          context.setAddCompletionChar(false);
          tailType.processTail(context.getEditor(), context.getTailOffset());
        }
        callback.nameChosen(item.getLookupString());
      }
    });
  }
}
