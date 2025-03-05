// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInspection.JavaSuppressionUtil;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.codeInspection.SuppressionUtilCore;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuppressAllForClassFix extends SuppressFix {
  private static final Logger LOG = Logger.getInstance(SuppressAllForClassFix.class);

  public SuppressAllForClassFix() {
    super(SuppressionUtil.ALL);
  }

  @Override
  public @Nullable PsiJavaDocumentedElement getContainer(final PsiElement element) {
    PsiJavaDocumentedElement container = super.getContainer(element);
    if (container == null) {
      return null;
    }
    while (container != null) {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(container, PsiClass.class);
      if (parentClass == null && container instanceof PsiClass && !(container instanceof PsiImplicitClass)) {
        return container;
      }
      container = parentClass;
    }
    return null;
  }

  @Override
  public @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("suppress.all.for.class");
  }

  @Override
  public void invoke(final @NotNull Project project, final @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiJavaDocumentedElement container = getContainer(element);
    LOG.assertTrue(container != null);
    if (container instanceof PsiModifierListOwner owner && use15Suppressions(container)) {
      final PsiModifierList modifierList = owner.getModifierList();
      if (modifierList != null) {
        final PsiAnnotation annotation = modifierList.findAnnotation(JavaSuppressionUtil.SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
        if (annotation != null) {
          String annoText = "@" + JavaSuppressionUtil.SUPPRESS_INSPECTIONS_ANNOTATION_NAME + "(\"" + SuppressionUtil.ALL + "\")";
          new CommentTracker().replaceAndRestoreComments(annotation, 
                                                         JavaPsiFacade.getElementFactory(project).createAnnotationFromText(annoText, container));
          return;
        }
      }
    }
    else {
      PsiDocComment docComment = container.getDocComment();
      if (docComment != null) {
        PsiDocTag noInspectionTag = docComment.findTagByName(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME);
        if (noInspectionTag != null) {
          String tagText = "@" + SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME + " " + SuppressionUtil.ALL;
          noInspectionTag.replace(JavaPsiFacade.getElementFactory(project).createDocTagFromText(tagText));
          return;
        }
      }
    }

    super.invoke(project, element);
  }

  @Override
  public int getPriority() {
    return 60;
  }
}
