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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.javadoc.PsiInlineDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.text.CharFilter.NOT_WHITESPACE_FILTER;

public class JavadocBlankLinesInspection extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitDocToken(PsiDocToken token) {
        super.visitDocToken(token);
        PsiElement nextWhitespace = token.getNextSibling();
        PsiElement prevWhitespace = token.getPrevSibling();
        if (token.getTokenType() == JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS &&
            prevWhitespace instanceof PsiWhiteSpace &&
            nextWhitespace instanceof PsiWhiteSpace &&
            !isAfterParagraphOrBlockTag(prevWhitespace) &&
            !isBeforeParagraphOrBlockTag(nextWhitespace) &&
            !isAfterPreTag(token)) {
          holder.registerProblem(token, JavaBundle.message("inspection.javadoc.blank.lines.message"), new InsertParagraphTagFix(token));
        }
      }
    };
  }

  private static boolean isAfterPreTag(PsiDocToken token) {
    PsiElement parent = token.getParent();
    if (parent instanceof PsiInlineDocTag) {
      return isAfterPreTagInner(PsiTreeUtil.getPrevSiblingOfType(parent, PsiDocToken.class));
    }
    return isAfterPreTagInner(token);
  }

  private static boolean isAfterPreTagInner(PsiDocToken token) {
    boolean result = false;
    while (token != null) {
      String text = token.getText();
      int closingPreTagIndex = StringUtil.toLowerCase(text).lastIndexOf("</pre>");
      int openingPreTagIndex = StringUtil.toLowerCase(text).lastIndexOf("<pre>");
      result = openingPreTagIndex != -1 && (closingPreTagIndex == -1 || closingPreTagIndex < openingPreTagIndex);
      if (closingPreTagIndex != -1 || openingPreTagIndex != -1) break;
      token = PsiTreeUtil.getPrevSiblingOfType(token, PsiDocToken.class);
    }
    return result;
  }

  private static boolean isAfterParagraphOrBlockTag(PsiElement element) {
    PsiElement prevSibling = element.getPrevSibling();
    if (!(prevSibling instanceof PsiDocToken)) return true;
    if (((PsiDocToken)prevSibling).getTokenType() != JavaDocTokenType.DOC_COMMENT_DATA) return true;
    String text = prevSibling.getText();
    return endsWithHtmlBlockTag(text) || isNullOrBlockTag(prevSibling);
  }

  private static boolean isBeforeParagraphOrBlockTag(PsiElement element) {
    PsiElement nextSibling = element.getNextSibling();
    if (!(nextSibling instanceof PsiDocToken)) return true;
    if (((PsiDocToken)nextSibling).getTokenType() != JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) return true;
    nextSibling = nextSibling.getNextSibling();
    if (nextSibling == null) return true;
    String text = nextSibling.getText();
    return isNullOrBlockTag(nextSibling) ||
           startsWithHtmlBlockTag(text) ||
           isNullOrBlockTag(nextSibling.getNextSibling());
  }

  private static boolean startsWithHtmlBlockTag(String text) {
    text = text.stripLeading();
    if (text.isEmpty() || text.charAt(0) != '<') return false;
    String maybeBlockTag = text.substring(1, text.indexOf('>'));
    String trimmed = StringUtil.trim(maybeBlockTag.strip(), ch -> NOT_WHITESPACE_FILTER.accept(ch) && ch != '/');
    return HtmlUtil.isHtmlBlockTag(trimmed) || "br".equalsIgnoreCase(trimmed);
  }

  private static boolean endsWithHtmlBlockTag(String text) {
    text = text.stripTrailing();
    if (text.isEmpty() || text.charAt(text.length() - 1) != '>') return false;
    String maybeBlockTag = text.substring(text.lastIndexOf('<') + 1, text.length() - 1);
    String trimmed = StringUtil.trim(maybeBlockTag.strip(), ch -> NOT_WHITESPACE_FILTER.accept(ch) && ch != '/');
    return HtmlUtil.isHtmlBlockTag(trimmed) || "br".equalsIgnoreCase(trimmed);
  }

  private static boolean isNullOrBlockTag(PsiElement element) {
    return element == null || (element instanceof PsiDocTag && !(element instanceof PsiInlineDocTag));
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
