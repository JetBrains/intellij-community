// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.javadoc;

import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.templateLanguages.TemplateLanguageUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Bas Leijdekkers
 */
public final class DanglingJavadocInspection extends BaseInspection {

  public boolean ignoreCopyright = true;

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("dangling.javadoc.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreCopyright", InspectionGadgetsBundle.message("dangling.javadoc.ignore.copyright.option")));
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    return new LocalQuickFix[] {
      new DeleteCommentFix(),
      new ConvertCommentFix()
    };
  }

  private static class ConvertCommentFix extends PsiUpdateModCommandQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("dangling.javadoc.convert.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement docComment = element.getParent();
      final StringBuilder newCommentText = new StringBuilder();
      for (PsiElement child = docComment.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child instanceof PsiDocToken docToken) {
          final IElementType tokenType = docToken.getTokenType();
          if (JavaDocTokenType.DOC_COMMENT_START.equals(tokenType)) {
            newCommentText.append("/*");
          }
          else if (!JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS.equals(tokenType)) {
            newCommentText.append(child.getText());
          }
        }
        else {
          newCommentText.append(child.getText());
        }
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiComment newComment = factory.createCommentFromText(newCommentText.toString(), element);
      docComment.replace(newComment);
    }
  }

  private static class DeleteCommentFix extends PsiUpdateModCommandQuickFix {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("dangling.javadoc.delete.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      element.getParent().delete();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DanglingJavadocVisitor();
  }

  private class DanglingJavadocVisitor extends BaseInspectionVisitor {

    @Override
    public void visitDocComment(@NotNull PsiDocComment comment) {
      super.visitDocComment(comment);
      if (comment.getOwner() != null || TemplateLanguageUtil.isInsideTemplateFile(comment)) {
        return;
      }
      if (JavaDocUtil.isInsidePackageInfo(comment) &&
          PsiTreeUtil.skipWhitespacesAndCommentsForward(comment) instanceof PsiPackageStatement &&
          "package-info.java".equals(comment.getContainingFile().getName())) {
        return;
      }
      if (ignoreCopyright && comment.getPrevSibling() == null && comment.getParent() instanceof PsiFile) {
        return;
      }
      registerError(comment.getFirstChild());
    }
  }
}
