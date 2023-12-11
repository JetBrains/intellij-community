// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.annotation;

import com.intellij.codeInsight.daemon.impl.quickfix.AddAnnotationAttributeNameFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateAnnotationMethodFromUsageFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class SingleElementAnnotationInspection extends BaseInspection {
  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExpandAnnotationVisitor();
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new ExpandAnnotationFix();
  }

  private static class ExpandAnnotationFix extends PsiUpdateModCommandQuickFix {
    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("single.element.annotation.quickfix");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("single.element.annotation.family.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiNameValuePair annotationParameter = (PsiNameValuePair)startElement;
      final String text = buildReplacementText(annotationParameter);
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(annotationParameter.getProject());
      final PsiAnnotation newAnnotation = factory.createAnnotationFromText("@A(" + text + " )", annotationParameter);
      annotationParameter.replace(newAnnotation.getParameterList().getAttributes()[0]);
    }

    private static String buildReplacementText(@NotNull PsiNameValuePair annotationParameter) {
      final PsiAnnotationMemberValue value = annotationParameter.getValue();
      return PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME + "=" + (value != null ? value.getText() : "");
    }
  }

  private static class ExpandAnnotationVisitor extends BaseInspectionVisitor {
    @Override
    public void visitNameValuePair(@NotNull PsiNameValuePair pair) {
      super.visitNameValuePair(pair);

      if (pair.getName() == null && pair.getValue() != null) {
        final PsiElement parent = pair.getParent();
        if (parent instanceof PsiAnnotationParameterList) {
          final Set<String> usedNames = AddAnnotationAttributeNameFix.getUsedAttributeNames((PsiAnnotationParameterList)parent);
          if (!usedNames.contains(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)) {
            final PsiReference reference = pair.getReference();
            if (reference != null) {
              final PsiElement resolved = reference.resolve();
              if (resolved instanceof PsiMethod) {
                final PsiAnnotationMemberValue value = pair.getValue();
                final PsiType valueType = CreateAnnotationMethodFromUsageFix.getAnnotationValueType(value);
                if (AddAnnotationAttributeNameFix.isCompatibleReturnType((PsiMethod)resolved, valueType)) {
                  registerError(pair);
                }
              }
            }
          }
        }
      }
    }
  }
}
