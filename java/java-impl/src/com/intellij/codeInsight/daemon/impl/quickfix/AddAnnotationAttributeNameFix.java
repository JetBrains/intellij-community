// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.stream.Collectors;

public class AddAnnotationAttributeNameFix extends PsiUpdateModCommandAction<PsiNameValuePair> {
  private final String myName;

  public AddAnnotationAttributeNameFix(PsiNameValuePair pair, String name) {
    super(pair);
    myName = name;
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return QuickFixBundle.message("add.annotation.attribute.name.family.name");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiNameValuePair element, @NotNull ModPsiUpdater updater) {
    doFix(element, myName);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiNameValuePair element) {
    return Presentation.of(QuickFixBundle.message("add.annotation.attribute.name", myName))
      .withFixAllOption(this);
  }

  public static @Unmodifiable @NotNull List<IntentionAction> createFixes(@NotNull PsiNameValuePair pair) {
    final PsiAnnotationMemberValue value = pair.getValue();
    if (value == null || pair.getName() != null) {
      return Collections.emptyList();
    }

    final Collection<String> methodNames = getAvailableAnnotationMethodNames(pair);
    return ContainerUtil.map(methodNames, name -> new AddAnnotationAttributeNameFix(pair, name).asIntention());
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

  private static @NotNull Collection<String> getAvailableAnnotationMethodNames(@NotNull PsiNameValuePair pair) {
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

  public static @NotNull Set<String> getUsedAttributeNames(@NotNull PsiAnnotationParameterList parameterList) {
    return Arrays.stream(parameterList.getAttributes())
              .map(PsiNameValuePair::getName)
              .filter(Objects::nonNull)
              .collect(Collectors.toSet());
  }

  private static @Nullable PsiClass getAnnotationClass(@NotNull PsiAnnotationParameterList parameterList) {
    PsiElement parent = parameterList.getParent();
    return parent instanceof PsiAnnotation ? ((PsiAnnotation)parent).resolveAnnotationType() : null;
  }
}
