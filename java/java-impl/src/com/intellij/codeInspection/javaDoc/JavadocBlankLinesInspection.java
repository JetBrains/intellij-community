// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.javadoc.PsiInlineDocTag;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavadocBlankLinesInspection extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitDocToken(PsiDocToken token) {
        super.visitDocToken(token);
        PsiElement nextSibling = token.getNextSibling();
        if (token.getTokenType() == JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS &&
            token.getPrevSibling() instanceof PsiWhiteSpace &&
            nextSibling instanceof PsiWhiteSpace && !isBeforeParagraphOrBlockTag(nextSibling)) {
          holder.registerProblem(token, JavaBundle.message("inspection.javadoc.blank.lines.message"), new InsertParagraphTagFix(token));
        }
      }
    };
  }

  private static boolean isBeforeParagraphOrBlockTag(PsiElement element) {
    PsiDocToken maybeLeadingAsterisks = ObjectUtils.tryCast(element.getNextSibling(), PsiDocToken.class);
    if (maybeLeadingAsterisks == null || maybeLeadingAsterisks.getTokenType() != JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
      return false;
    }
    PsiElement nextSibling = maybeLeadingAsterisks.getNextSibling();
    if (nextSibling == null) return false;
    return nextSibling.getText().stripLeading().startsWith("<p>") ||
           isBlockTag(nextSibling) ||
           isBlockTag(nextSibling.getNextSibling());
  }

  private static boolean isBlockTag(PsiElement element) {
    return element instanceof PsiDocTag && !(element instanceof PsiInlineDocTag);
  }

  private static class InsertParagraphTagFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    protected InsertParagraphTagFix(@Nullable PsiElement element) {
      super(element);
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile file,
                       @Nullable Editor editor,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {
      Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      if (document == null) return;
      TextRange range = startElement.getTextRange();
      document.replaceString(range.getStartOffset(), range.getEndOffset(), "* <p>");
    }

    @Override
    public @NotNull String getText() {
      return JavaBundle.message("inspection.javadoc.blank.lines.fix.name");
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.javadoc.blank.lines.fix.family.name");
    }
  }
}
