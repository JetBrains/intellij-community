// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class AddAnnotationAttributeNameFix extends LocalQuickFixAndIntentionActionOnPsiElement implements IntentionActionWithFixAllOption {
  private final String myName;

  public AddAnnotationAttributeNameFix(PsiNameValuePair pair, String name) {
    super(pair);
    myName = name;
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("add.annotation.attribute.name", myName);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("add.annotation.attribute.name.family.name");
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    doFix((PsiNameValuePair)startElement, myName);
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return super.isAvailable(project, file, startElement, endElement) && startElement instanceof PsiNameValuePair;
  }

  @NotNull
  public static List<IntentionAction> createFixes(@NotNull PsiNameValuePair pair) {
    final PsiAnnotationMemberValue value = pair.getValue();
    if (value == null || pair.getName() != null) {
      return Collections.emptyList();
    }

    final Collection<String> methodNames = getAvailableAnnotationMethodNames(pair);
    return ContainerUtil.map(methodNames, name -> new AddAnnotationAttributeNameFix(pair, name));
  }

  public static void doFix(@NotNull PsiNameValuePair annotationParameter, @NotNull String name) {
    final String text = buildReplacementText(annotationParameter, name);
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(annotationParameter.getProject());
    final PsiAnnotation newAnnotation = factory.createAnnotationFromText("@A(" + text + " )", annotationParameter);
    annotationParameter.replace(newAnnotation.getParameterList().getAttributes()[0]);
  }

  private static String buildReplacementText(@NotNull PsiNameValuePair annotationParameter, @NotNull String name) {
    final PsiAnnotationMemberValue value = annotationParameter.getValue();
    return value != null ? name + "=" + value.getText() : name + "=";
  }

  public static boolean isCompatibleReturnType(@NotNull PsiMethod psiMethod, @Nullable PsiType valueType) {
    final PsiType expectedType = psiMethod.getReturnType();
    if (expectedType == null || valueType == null || expectedType.isAssignableFrom(valueType)) {
      return true;
    }
    if (expectedType instanceof PsiArrayType) {
      final PsiType componentType = ((PsiArrayType)expectedType).getComponentType();
      return componentType.isAssignableFrom(valueType);
    }
    return false;
  }

  @NotNull
  private static Collection<String> getAvailableAnnotationMethodNames(@NotNull PsiNameValuePair pair) {
    final PsiAnnotationMemberValue value = pair.getValue();
    if (value != null && pair.getName() == null) {
      final PsiElement parent = pair.getParent();
      if ((parent instanceof PsiAnnotationParameterList parameterList)) {
        final PsiClass annotationClass = getAnnotationClass(parameterList);

        if (annotationClass != null) {
          final Set<String> usedNames = getUsedAttributeNames(parameterList);

          final Collection<PsiMethod> availableMethods = Arrays.stream(annotationClass.getMethods())
            .filter(PsiAnnotationMethod.class::isInstance)
            .filter(psiMethod -> !usedNames.contains(psiMethod.getName())).toList();

          if (!availableMethods.isEmpty()) {
            final PsiType valueType = CreateAnnotationMethodFromUsageFix.getAnnotationValueType(value);
            return availableMethods.stream()
              .filter(psiMethod -> isCompatibleReturnType(psiMethod, valueType))
              .map(PsiMethod::getName)
              .collect(Collectors.toSet());
          }
        }
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  public static Set<String> getUsedAttributeNames(@NotNull PsiAnnotationParameterList parameterList) {
    return Arrays.stream(parameterList.getAttributes())
              .map(PsiNameValuePair::getName)
              .filter(Objects::nonNull)
              .collect(Collectors.toSet());
  }

  @Nullable
  private static PsiClass getAnnotationClass(@NotNull PsiAnnotationParameterList parameterList) {
    PsiElement parent = parameterList.getParent();
    return parent instanceof PsiAnnotation ? ((PsiAnnotation)parent).resolveAnnotationType() : null;
  }
}
