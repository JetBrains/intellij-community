// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.javadoc;

import com.intellij.codeInspection.*;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.javadoc.PsiInlineDocTag;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HtmlTagCanBeJavadocTagInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("html.tag.can.be.javadoc.tag.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new HtmlTagCanBeJavaDocTagFix();
  }

  private static class HtmlTagCanBeJavaDocTagFix extends ModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "{@code ...}");
    }

    @Override
    public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      return ModCommand.psiUpdate(descriptor.getStartElement(), e -> applyFix(e, descriptor.getTextRangeInElement()));
    }

    protected void applyFix(@NotNull PsiElement element, @NotNull TextRange range) {
      PsiFile file = element.getContainingFile();
      Document document = file.getViewProvider().getDocument();
      if (document == null) {
        return;
      }
      final int startOffset = range.getStartOffset();
      final int replaceStartOffset = element.getTextOffset() + startOffset;
      int startTag = range.getEndOffset();
      @NonNls String text = element.getText();
      if (!"<code>".equalsIgnoreCase(text.substring(startOffset, startTag))) {
        return;
      }
      @NonNls final StringBuilder newCommentText = new StringBuilder("{@code");
      int endTag = StringUtil.indexOfIgnoreCase(text, "</code>", startTag);
      while (endTag < 0) {
        appendElementText(text, startTag, text.length(), newCommentText);
        element = element.getNextSibling();
        if (element == null) return;
        startTag = 0;
        text = element.getText();
        endTag = StringUtil.indexOfIgnoreCase(text, "</code>", 0);
      }
      appendElementText(text, startTag, endTag, newCommentText);
      newCommentText.append('}');
      final int replaceEndOffset = element.getTextOffset() + endTag + 7;
      final String oldText = document.getText(new TextRange(replaceStartOffset, replaceEndOffset));
      if (!StringUtil.startsWithIgnoreCase(oldText, "<code>") || !StringUtil.endsWithIgnoreCase(oldText, "</code>")) { // sanity check
        return;
      }
      document.replaceString(replaceStartOffset, replaceEndOffset, newCommentText);
    }

    private static void appendElementText(String text, int startOffset, int endOffset, StringBuilder out) {
      if (out.length() == "{@code".length() && endOffset - startOffset > 0 && !Character.isWhitespace(text.charAt(startOffset))) {
        out.append(' ');
      }
      final String s = text.substring(startOffset, endOffset);
      out.append(StringUtil.unescapeXmlEntities(s));
    }
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new HtmlTagCanBeJavaDocTagVisitor();
  }

  private static class HtmlTagCanBeJavaDocTagVisitor extends BaseInspectionVisitor {
    @Override
    public void visitDocToken(@NotNull PsiDocToken token) {
      super.visitDocToken(token);
      final IElementType tokenType = token.getTokenType();
      if (!JavaDocTokenType.DOC_COMMENT_DATA.equals(tokenType)) {
        return;
      }
      @NonNls final String text = token.getText();
      int startIndex = 0;
      while (true) {
        startIndex = StringUtil.indexOfIgnoreCase(text, "<code>", startIndex);
        if (startIndex < 0) {
          return;
        }
        if (hasMatchingCloseTag(token, startIndex + 6)) {
          registerErrorAtOffset(token, startIndex, 6);
        }
        startIndex++;
      }
    }

    private static boolean hasMatchingCloseTag(PsiElement element, int offset) {
      int balance = 0;
      while (element != null) {
        @NonNls final String text = element.getText();
        final int endIndex = StringUtil.indexOfIgnoreCase(text, "</code>", offset);
        final int end = endIndex >= 0 ? endIndex : text.length();
        if (text.equals("{")) {
          balance++;
        }
        else if (text.equals("}")) {
          balance--;
          if (balance < 0) return false;
        }
        if (containsHtmlTag(text, offset, end)) {
          return false;
        }
        if (endIndex >= 0) {
          return balance == 0;
        }
        offset = 0;
        element = element.getNextSibling();
        if (element instanceof PsiInlineDocTag) {
          return false;
        }
      }
      return false;
    }
  }

  private static final Pattern START_TAG_PATTERN = Pattern.compile("<([a-zA-Z])+([^>])*>");

  static boolean containsHtmlTag(String text, int startIndex, int endIndex) {
    final Matcher matcher = START_TAG_PATTERN.matcher(text);
    if (matcher.find(startIndex)) {
      return matcher.start() < endIndex;
    }
    return false;
  }
}
