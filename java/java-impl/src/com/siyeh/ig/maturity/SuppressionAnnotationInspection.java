// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.maturity;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.pane;

public class SuppressionAnnotationInspection extends BaseInspection {
  public List<String> myAllowedSuppressions = new ArrayList<>();

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(OptPane.stringList("myAllowedSuppressions", JavaBundle.message("ignored.suppressions")));
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final boolean suppressionIdPresent = ((Boolean)infos[1]).booleanValue();
    if (infos[0] instanceof PsiAnnotation annotation) {
      return suppressionIdPresent
             ? new LocalQuickFix[]{new RemoveAnnotationQuickFix(annotation, null), new AllowSuppressionsFix()}
             : new LocalQuickFix[]{new RemoveAnnotationQuickFix(annotation, null),};
    } else if (infos[0] instanceof PsiComment) {
      return suppressionIdPresent
             ? new LocalQuickFix[]{new RemoveSuppressCommentFix(), new AllowSuppressionsFix()}
             : new LocalQuickFix[]{new RemoveSuppressCommentFix()};
    }
    return InspectionGadgetsFix.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "inspection.suppression.annotation.problem.descriptor");
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public SuppressQuickFix @NotNull [] getBatchSuppressActions(@Nullable PsiElement element) {
    return SuppressQuickFix.EMPTY_ARRAY;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuppressionAnnotationVisitor();
  }

  private static class RemoveSuppressCommentFix extends PsiUpdateModCommandQuickFix {
    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      startElement.delete();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("remove.suppress.comment.fix.family.name", SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME);
    }
  }

  private class AllowSuppressionsFix extends ModCommandQuickFix {
    @Override
    public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      final Iterable<String> ids;
      if (psiElement instanceof PsiAnnotation) {
        ids = JavaSuppressionUtil.getInspectionIdsSuppressedInAnnotation((PsiModifierList)psiElement.getParent());
      }
      else {
        final String suppressedIds = JavaSuppressionUtil.getSuppressedInspectionIdsIn(psiElement);
        if (suppressedIds == null) {
          return ModCommand.nop();
        }
        ids = StringUtil.tokenize(suppressedIds, ",");
      }
      return ModCommand.updateOption(psiElement, SuppressionAnnotationInspection.this, inspection -> {
        for (String id : ids) {
          if (!inspection.myAllowedSuppressions.contains(id)) {
            inspection.myAllowedSuppressions.add(id);
          }
        }
      });
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("allow.suppressions.fix.text");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("allow.suppressions.fix.family.name");
    }
  }

  private class SuppressionAnnotationVisitor extends BaseInspectionVisitor {
    @Override
    public void visitComment(@NotNull PsiComment comment) {
      super.visitComment(comment);
      final IElementType tokenType = comment.getTokenType();
      if (!tokenType.equals(JavaTokenType.END_OF_LINE_COMMENT)
          && !tokenType.equals(JavaTokenType.C_STYLE_COMMENT)) {
        return;
      }
      final String commentText = comment.getText();
      if (commentText.length() <= 2) {
        return;
      }
      @NonNls final String strippedComment = commentText.substring(2).trim();
      if (!strippedComment.startsWith(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME)) {
        return;
      }
      final String suppressedIds = JavaSuppressionUtil.getSuppressedInspectionIdsIn(comment);
      if (suppressedIds == null) {
        registerError(comment, comment, Boolean.FALSE);
        return;
      }
      final Iterable<String> ids = StringUtil.tokenize(suppressedIds, ",");
      for (String id : ids) {
        if (!myAllowedSuppressions.contains(id)) {
          registerError(comment, comment, Boolean.TRUE);
          break;
        }
      }
    }

    @Override
    public void visitAnnotation(@NotNull PsiAnnotation annotation) {
      super.visitAnnotation(annotation);
      final PsiJavaCodeReferenceElement reference = annotation.getNameReferenceElement();
      if (reference == null) {
        return;
      }
      @NonNls final String text = reference.getText();
      if ("SuppressWarnings".equals(text) ||
          BatchSuppressManager.SUPPRESS_INSPECTIONS_ANNOTATION_NAME.equals(text)) {
        final PsiElement annotationParent = annotation.getParent();
        if (annotationParent instanceof PsiModifierList) {
          final Collection<String> ids = JavaSuppressionUtil.getInspectionIdsSuppressedInAnnotation((PsiModifierList)annotationParent);
          if (!myAllowedSuppressions.containsAll(ids)) {
            registerError(annotation, annotation, Boolean.TRUE);
          }
          else if (ids.isEmpty()) {
            registerError(annotation, annotation, Boolean.FALSE);
          }
        }
      }
    }
  }
}
