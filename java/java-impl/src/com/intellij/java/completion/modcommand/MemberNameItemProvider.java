// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.ASTNode;
import com.intellij.modcompletion.CommonCompletionItem;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.util.JavaNameSuggestionUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiClass;
import static com.intellij.patterns.PsiJavaPatterns.psiField;
import static com.intellij.patterns.StandardPatterns.or;

@NotNullByDefault
final class MemberNameItemProvider extends JavaModCompletionItemProvider {
  public static final ElementPattern<PsiElement> INSIDE_TYPE_PARAMS_PATTERN = psiElement().
    afterLeaf(psiElement().withText("?").andOr(
      psiElement().afterLeaf("<", ","),
      psiElement().afterSiblingSkipping(psiElement().whitespaceCommentEmptyOrError(), psiElement(PsiAnnotation.class))));

  private static final int MAX_SCOPE_SIZE_TO_SEARCH_UNRESOLVED = 50000;

  @Override
  public void provideItems(CompletionContext parameters, ModCompletionResult sink) {
    if (parameters.getCompletionType() != CompletionType.BASIC && parameters.getCompletionType() != CompletionType.SMART ||
        parameters.getInvocationCount() == 0) {
      return;
    }

    PsiElement position = parameters.getPosition();
    Set<ModCompletionItem> lookupSet = new LinkedHashSet<>(); // LinkedHashSet for test stability
    PrefixMatcher matcher = parameters.matcher();
    if (psiElement(PsiIdentifier.class).andNot(INSIDE_TYPE_PARAMS_PATTERN).withParent(
      or(psiElement(PsiLocalVariable.class), psiElement(PsiParameter.class))).accepts(position)) {
      completeLocalVariableName(lookupSet, matcher, (PsiVariable)parameters.getPosition().getParent(),
                                parameters.getInvocationCount() >= 1);
      lookupSet = StreamEx.of(lookupSet)
        .map(item -> item instanceof CommonCompletionItem cci ? cci.withAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE) : item)
        .toCollection(LinkedHashSet::new);
    }

    if (psiElement(PsiIdentifier.class).withParent(PsiField.class).andNot(INSIDE_TYPE_PARAMS_PATTERN).accepts(position)) {
      PsiField variable = (PsiField)parameters.getPosition().getParent();
      completeMethodName(lookupSet, variable, matcher);
      completeFieldName(lookupSet, variable, matcher, parameters.getInvocationCount() >= 1);
    }

    if (psiElement(PsiIdentifier.class).withParent(PsiRecordComponent.class).andNot(INSIDE_TYPE_PARAMS_PATTERN).accepts(position)) {
      PsiRecordComponent component = (PsiRecordComponent)parameters.getPosition().getParent();
      completeComponentName(lookupSet, component, matcher);
    }

    if (PsiJavaPatterns.psiElement().nameIdentifierOf(PsiJavaPatterns.psiMethod().withParent(PsiClass.class)).accepts(position) &&
        !(PsiUtil.isJavaToken(PsiTreeUtil.prevVisibleLeaf(position), JavaTokenType.DOT))) {
      completeMethodName(lookupSet, parameters.getPosition().getParent(), matcher);
    }

    lookupSet.forEach(sink);
  }

  private static void completeLocalVariableName(Set<ModCompletionItem> set,
                                                PrefixMatcher matcher,
                                                PsiVariable var,
                                                boolean includeOverlapped) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");
    Project project = var.getProject();
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    VariableKind variableKind = codeStyleManager.getVariableKind(var);

    String propertyName = null;
    if (variableKind == VariableKind.PARAMETER) {
      PsiMethod method = PsiTreeUtil.getParentOfType(var, PsiMethod.class);
      if (method != null) {
        propertyName = PropertyUtilBase.getPropertyName(method);
      }
      if (method != null && method.getName().startsWith("with")) {
        propertyName = StringUtil.decapitalize(method.getName().substring(4));
      }
    }

    PsiType type = var.getType();
    if (type instanceof PsiClassType classType &&
        classType.resolve() == null &&
        seemsMistypedKeyword(classType.getClassName())) {
      return;
    }

    SuggestedNameInfo
      suggestedNameInfo =
      codeStyleManager.suggestVariableName(variableKind, propertyName, null, type, StringUtil.isEmpty(matcher.getPrefix()));
    suggestedNameInfo = codeStyleManager.suggestUniqueVariableName(suggestedNameInfo, var, false);
    String[] suggestedNames = suggestedNameInfo.names;
    addLookupItems(set, matcher, project, var, suggestedNames);
    if (!hasStartMatches(set, matcher)) {
      Set<String> setOfNames = Arrays.stream(suggestedNames).collect(Collectors.toSet());
      String objectName = "object";
      if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) && matcher.prefixMatches(objectName) &&
          (!setOfNames.contains(objectName) || !ContainerUtil.exists(set, t -> t.mainLookupString().equals(objectName)))) {
        set.add(new CommonCompletionItem(objectName));
      }
      String stringName = "string";
      if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING) && matcher.prefixMatches(stringName) &&
          (!setOfNames.contains(stringName) || !ContainerUtil.exists(set, t -> t.mainLookupString().equals(stringName)))) {
        set.add(new CommonCompletionItem(stringName));
      }
    }

    if (!hasStartMatches(set, matcher) && includeOverlapped) {
      addLookupItems(set, matcher, project, var, getOverlappedNameVersions(matcher.getPrefix(), suggestedNames, ""));
    }
    PsiElement parent = PsiTreeUtil.getParentOfType(var, PsiCodeBlock.class);
    if (parent == null) parent = PsiTreeUtil.getParentOfType(var, PsiMethod.class, PsiLambdaExpression.class);
    addLookupItems(set, matcher, project, var, getUnresolvedReferences(parent, false, matcher));
    if (var instanceof PsiParameter && parent instanceof PsiMethod method) {
      addSuggestionsInspiredByFieldNames(set, matcher, var, project, codeStyleManager);
      PsiDocComment docComment = method.getDocComment();
      if (docComment != null) {
        addLookupItems(set, matcher, project, var, ArrayUtil.toStringArray(getUnresolvedMethodParamNamesFromJavadoc(docComment)));
      }
    }

    PsiExpression initializer = var.getInitializer();
    if (initializer != null) {
      SuggestedNameInfo initializerSuggestions = CommonJavaRefactoringUtil.getSuggestedName(type, initializer);
      addLookupItems(set, matcher, project, var, initializerSuggestions.names);
    }
  }

  private static List<String> getUnresolvedMethodParamNamesFromJavadoc(PsiDocComment docComment) {
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

  private static boolean seemsMistypedKeyword(@Nullable String className) {
    return className != null && !StringUtil.isCapitalized(className);
  }

  private static boolean hasStartMatches(PrefixMatcher matcher, Set<String> set) {
    return ContainerUtil.exists(set, matcher::isStartMatch);
  }

  private static boolean hasStartMatches(Set<? extends ModCompletionItem> set, PrefixMatcher matcher) {
    return ContainerUtil.exists(set, lookupElement -> matcher.isStartMatch(lookupElement.mainLookupString()) ||
                                                      hasStartMatches(matcher, lookupElement.additionalLookupStrings()));
  }

  private static void addSuggestionsInspiredByFieldNames(Set<ModCompletionItem> set,
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
        addLookupItems(set, matcher, project, var, codeStyleManager.propertyNameToVariableName(prop, VariableKind.PARAMETER));
      }
    }
  }

  private static String[] getOverlappedNameVersions(String prefix, String[] suggestedNames, String suffix) {
    List<String> newSuggestions = new ArrayList<>();
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
      int overlap = getOverlap(suggestedName, prefix);

      if (overlap < longestOverlap) continue;

      if (overlap > longestOverlap) {
        newSuggestions.clear();
        longestOverlap = overlap;
      }

      String suggestion = prefix.substring(0, prefix.length() - overlap) + suggestedName;

      int lastIndexOfSuffix = suggestion.lastIndexOf(suffix);
      if (lastIndexOfSuffix >= 0 && suffix.length() < suggestion.length() - lastIndexOfSuffix) {
        suggestion = suggestion.substring(0, lastIndexOfSuffix) + suffix;
      }

      if (!newSuggestions.contains(suggestion)) {
        newSuggestions.add(suggestion);
      }
    }
    return ArrayUtilRt.toStringArray(newSuggestions);
  }

  private static int getOverlap(String propertyName, String prefix) {
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

  private static String[] getUnresolvedReferences(@Nullable PsiElement parentOfType, boolean referenceOnMethod, PrefixMatcher matcher) {
    if (parentOfType != null && parentOfType.getTextLength() > MAX_SCOPE_SIZE_TO_SEARCH_UNRESOLVED) return ArrayUtilRt.EMPTY_STRING_ARRAY;
    Set<String> unresolvedRefs = new LinkedHashSet<>();

    if (parentOfType != null) {
      parentOfType.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(PsiReferenceExpression reference) {
          PsiElement parent = reference.getParent();
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

  private static void completeComponentName(Set<ModCompletionItem> set, PsiRecordComponent var, PrefixMatcher matcher) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");

    Project project = var.getProject();
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);

    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, var.getType());
    String[] suggestedNames = suggestedNameInfo.names;
    addLookupItems(set, matcher, project, var, suggestedNames);
  }

  private static void completeFieldName(Set<ModCompletionItem> set,
                                        PsiField var,
                                        PrefixMatcher matcher,
                                        boolean includeOverlapped) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");

    Project project = var.getProject();
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    VariableKind variableKind = JavaCodeStyleManager.getInstance(project).getVariableKind(var);

    String prefix = matcher.getPrefix();
    if (PsiTypes.voidType().equals(var.getType()) ||
        psiField().inClass(psiClass().isInterface().andNot(psiClass().isAnnotationType())).accepts(var)) {
      completeVariableNameForRefactoring(project, set, matcher, var.getType(), variableKind, includeOverlapped, true);
      return;
    }

    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(variableKind, null, null, var.getType());
    String[] suggestedNames = suggestedNameInfo.names;
    addLookupItems(set, matcher, project, var, suggestedNames);

    if (!hasStartMatches(set, matcher) && includeOverlapped) {
      // use suggested names as suffixes
      String requiredSuffix = codeStyleManager.getSuffixByVariableKind(variableKind);
      if (variableKind != VariableKind.STATIC_FINAL_FIELD) {
        for (int i = 0; i < suggestedNames.length; i++) {
          suggestedNames[i] = codeStyleManager.variableNameToPropertyName(suggestedNames[i], variableKind);
        }
      }


      addLookupItems(set, matcher, project, var, getOverlappedNameVersions(prefix, suggestedNames, requiredSuffix));
    }

    addLookupItems(set, matcher, project, var, getUnresolvedReferences(var.getParent(), false, matcher));

    PsiExpression initializer = var.getInitializer();
    PsiClass containingClass = var.getContainingClass();
    if (initializer != null && containingClass != null) {
      SuggestedNameInfo initializerSuggestions = JavaNameSuggestionUtil.suggestFieldName(
        var.getType(), null, initializer, var.hasModifierProperty(PsiModifier.STATIC), containingClass);
      addLookupItems(set, matcher, project, var, initializerSuggestions.names);
    }
  }

  static void completeVariableNameForRefactoring(Project project,
                                                 Set<ModCompletionItem> set,
                                                 PrefixMatcher matcher,
                                                 PsiType varType,
                                                 VariableKind varKind, boolean includeOverlapped, boolean methodPrefix) {
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(varKind, null, null, varType);
    String[] strings = completeVariableNameForRefactoring(codeStyleManager, matcher, varType, varKind, suggestedNameInfo,
                                                          includeOverlapped, methodPrefix);
    addLookupItems(set, matcher, project, null, strings);
  }

  static String[] completeVariableNameForRefactoring(JavaCodeStyleManager codeStyleManager,
                                                     PrefixMatcher matcher,
                                                     @Nullable PsiType varType,
                                                     VariableKind varKind,
                                                     SuggestedNameInfo suggestedNameInfo,
                                                     boolean includeOverlapped, boolean methodPrefix) {
    Set<String> result = new LinkedHashSet<>();
    String[] suggestedNames = suggestedNameInfo.names;
    for (String suggestedName : suggestedNames) {
      if (matcher.prefixMatches(suggestedName)) {
        result.add(suggestedName);
      }
    }

    if (!hasStartMatches(matcher, result) && !PsiTypes.voidType().equals(varType) && includeOverlapped) {
      // use suggested names as suffixes
      String requiredSuffix = codeStyleManager.getSuffixByVariableKind(varKind);
      String prefix = matcher.getPrefix();
      if (varKind != VariableKind.STATIC_FINAL_FIELD || methodPrefix) {
        for (int i = 0; i < suggestedNames.length; i++) {
          suggestedNames[i] = codeStyleManager.variableNameToPropertyName(suggestedNames[i], varKind);
        }
      }

      ContainerUtil.addAll(result, getOverlappedNameVersions(prefix, suggestedNames, requiredSuffix));
    }
    return ArrayUtilRt.toStringArray(result);
  }

  private static void completeMethodName(Set<ModCompletionItem> set, PsiElement element, PrefixMatcher matcher) {
    if (element instanceof PsiMethod method && method.isConstructor()) {
      PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && !(containingClass instanceof PsiImplicitClass)) {
        String name = containingClass.getName();
        if (StringUtil.isNotEmpty(name)) {
          addLookupItems(set, matcher, element.getProject(), element, name);
        }
      }
      return;
    }

    PsiClass ourClassParent = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (ourClassParent == null) return;

    if (ourClassParent.isAnnotationType() && matcher.prefixMatches(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)) {
      set.add(new CommonCompletionItem(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)
                .withPresentation(new ModCompletionItemPresentation(
                  MarkupText.plainText(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME).concat("()", MarkupText.Kind.GRAYED))
                                    .withMainIcon(() -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Method)))
                .withAdditionalUpdater((completionStart, updater) -> {
                })); // TODO: parentheses
    }

    if (element instanceof PsiField field && field.hasModifierProperty(PsiModifier.STATIC) &&
        ourClassParent.equals(PsiUtil.resolveClassInClassTypeOnly(field.getType()))) {
      set.add(new CommonCompletionItem("getInstance")
                .withPresentation(new ModCompletionItemPresentation(
                  MarkupText.plainText("getInstance").concat("()", MarkupText.Kind.GRAYED))
                                    .withMainIcon(() -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Method)))
                .withAdditionalUpdater((completionStart, updater) -> {
                })); // TODO: parentheses with parameters
    }
    addLookupItems(set, matcher, element.getProject(), element, getUnresolvedReferences(ourClassParent, true, matcher));

    addLookupItems(set, matcher, element.getProject(), element, getPropertiesHandlersNames(
      ourClassParent,
      ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC),
      PsiUtil.getTypeByPsiElement(element), element));
  }

  private static String[] getPropertiesHandlersNames(PsiClass psiClass,
                                                     boolean staticContext,
                                                     @Nullable PsiType varType,
                                                     PsiElement element) {
    List<String> propertyHandlers = new ArrayList<>();

    for (PsiField field : psiClass.getFields()) {
      if (field == element) continue;
      if (StringUtil.isEmpty(field.getName())) continue;

      PsiUtilCore.ensureValid(field);
      PsiType fieldType = field.getType();
      PsiUtil.ensureValidType(fieldType);

      PsiModifierList modifierList = field.getModifierList();
      if (staticContext && modifierList != null && !modifierList.hasModifierProperty(PsiModifier.STATIC)) continue;

      if (fieldType.equals(varType)) {
        String getterName = JavaPsiRecordUtil.getComponentForField(field) != null ?
                            field.getName() : PropertyUtilBase.suggestGetterName(field);
        if (psiClass.findMethodsByName(getterName, true).length == 0 ||
            psiClass.findMethodBySignature(GenerateMembersUtil.generateGetterPrototype(field), true) == null) {
          propertyHandlers.add(getterName);
        }
      }

      if (PsiTypes.voidType().equals(varType)) {
        String setterName = PropertyUtilBase.suggestSetterName(field);
        if (psiClass.findMethodsByName(setterName, true).length == 0 ||
            psiClass.findMethodBySignature(GenerateMembersUtil.generateSetterPrototype(field), true) == null) {
          propertyHandlers.add(setterName);
        }
      }
    }

    return ArrayUtilRt.toStringArray(propertyHandlers);
  }

  private static void addLookupItems(
    Set<ModCompletionItem> lookupElements,
    PrefixMatcher matcher, Project project,
    @Nullable PsiElement context,
    String... strings
  ) {
    LanguageLevel languageLevel = context != null ? PsiUtil.getLanguageLevel(context) : PsiUtil.getLanguageLevel(project);
    outer:
    for (int i = 0; i < strings.length; i++) {
      String name = strings[i];
      if (!matcher.prefixMatches(name) || !PsiNameHelper.getInstance(project).isIdentifier(name, languageLevel)) {
        continue;
      }

      for (ModCompletionItem lookupElement : lookupElements) {
        if (lookupElement.mainLookupString().equals(name) || lookupElement.additionalLookupStrings().contains(name)) {
          continue outer;
        }
      }

      ModCompletionItem element = new CommonCompletionItem(name)
        .withAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE)
        .withPriority(-i);
      lookupElements.add(element);
    }
  }
}
