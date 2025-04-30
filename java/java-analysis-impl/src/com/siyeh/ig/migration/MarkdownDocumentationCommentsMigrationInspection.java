// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.psi.javadoc.PsiDocToken.isDocToken;

/**
 * @author Bas Leijdekkers
 */
@ApiStatus.Internal
public final class MarkdownDocumentationCommentsMigrationInspection extends BaseInspection implements DumbAware {
  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("markdown.documentation.comments.migration.display.name");
  }

  @Override
  protected @NotNull LocalQuickFix buildFix(Object... infos) {
    return new MarkdownDocumentationCommentsMigrationFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MarkdownDocumentationCommentsMigrationVisitor();
  }

  private static class MarkdownDocumentationCommentsMigrationVisitor extends BaseInspectionVisitor {
    @Override
    public void visitDocComment(@NotNull PsiDocComment comment) {
      super.visitDocComment(comment);
      if (comment.isMarkdownComment()) {
        return;
      }
      registerError(isVisibleHighlight(comment) ? comment.getFirstChild() : comment);
    }
  }

  private static class MarkdownDocumentationCommentsMigrationFix extends PsiUpdateModCommandQuickFix implements DumbAware {

    private static final TokenSet SKIP_TOKENS = TokenSet.create(JavaDocTokenType.DOC_COMMENT_START,
                                                                JavaDocTokenType.DOC_COMMENT_END,
                                                                JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS);
    private static final Pattern HEADING = Pattern.compile("[hH]([1-6])");

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("markdown.documentation.comments.migration.fix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiDocToken) element = element.getParent();
      if (!(element instanceof PsiDocComment)) return;
      String markdown = convertToMarkdown(appendElementText(element, new StringBuilder()).toString());
      String indent = getElementIndent(element);
      String[] lines = markdown.split("\n");
      StringBuilder result = new StringBuilder(markdown.length() + (indent.length() + 4) * lines.length);
      for (String line : lines) {
        if (!result.isEmpty()) {
          result.append(indent);
        }
        result.append("///").append(line).append('\n');
      }
      result.append(indent);

      Document document = element.getContainingFile().getFileDocument();
      int startOffset = element.getTextOffset();
      int endOffset = element.getNextSibling() instanceof PsiWhiteSpace whiteSpace 
                      ? whiteSpace.getTextOffset() + whiteSpace.getTextLength() 
                      : startOffset + element.getTextLength(); 
      document.replaceString(startOffset, endOffset, result);
    }

    private static StringBuilder appendElementText(@NotNull PsiElement element, StringBuilder result) {
      for (@NotNull PsiElement child : element.getChildren()) {
        if (isDocToken(child, SKIP_TOKENS)) continue;

        if (child instanceof PsiInlineDocTag inlineDocTag) {
          PsiElement next = inlineDocTag.getNameElement().getNextSibling();
          if (next instanceof PsiWhiteSpace && next.getText().contains("\n")) result.append("\n ");
          String name = inlineDocTag.getName();
          if ("code".equals(name)) handleCode(inlineDocTag, result);
          else if ("link".equals(name)) handleLink(inlineDocTag, result);
          else handleInlineDocTag(inlineDocTag, result);
        }
        else if (child instanceof PsiDocParamRef) {
          result.append('\u0000').append(child.getText()).append('\u0000');
        }
        else if (child instanceof PsiDocTag || child instanceof PsiDocTagValue) {
          appendElementText(child, result);
        }
        else if (child instanceof PsiWhiteSpace) {
          if (!isDocToken(child.getNextSibling(), JavaDocTokenType.DOC_COMMENT_END)) {
            String text = child.getText();
            if (text.contains("\n")) {
              if (!result.isEmpty()) result.append("\n");
            }
            else result.append(text);
          }
        }
        else result.append(child.getText());
      }
      return result;
    }

    private enum Context { PLAIN, LIST, PRE }

    private static String convertToMarkdown(String html) {
      int tag = -1;
      boolean endTag = false;
      boolean newLine = false;
      Context context = Context.PLAIN;
      StringBuilder result = new StringBuilder();
      for (int i = 0, length = html.length(); i < length; i++) {
        char c = html.charAt(i);
        if (tag >= 0) {
          if (isLetterOrDigitAscii(c)) {
            continue;
          }
          else if (c == '/') {
            if (i == tag + 1) endTag = true;
            continue;
          }
          else if (c == '>') {
            int start = tag + (endTag ? 2 : 1);
            int end = (!endTag && html.charAt(i - 1) == '/') ? i - 1 : i;
            String name = html.substring(start, end).trim().toLowerCase(Locale.ENGLISH);
            Matcher matcher; 
            if ("li".equals(name)) {
              if (endTag) {
                context = Context.PLAIN;
              }
              else {
                if (result.length() > 4 && "    ".equals(result.substring(result.length() - 4))) {
                  result.delete(result.length() - 4, result.length());
                }
                result.append("  - ");
                context = Context.LIST;
              }
            }
            else if ("em".equals(name) || "i".equals(name)) {
              result.append('_');
            }
            else if ("b".equals(name) || "strong".equals(name)) {
              result.append("**");
            }
            else if ("hr".equals(name)) {
              result.append("---");
            }
            else if ("p".equals(name)) {
              if (i + 1 < length && html.charAt(i + 1) != '\n') result.append("\n ");
            }
            else if ("br".equals(name)) {
              result.append("  ");
              if (i + 1 < length && html.charAt(i + 1) != '\n') result.append('\n');
            }
            else if ("ul".equals(name)) {
              if (endTag) context = Context.PLAIN;
            }
            else if ("pre".equals(name)) {
              context = endTag ? Context.PLAIN : Context.PRE;
              result.append(html, tag, i + 1);
            }
            else if ((matcher = HEADING.matcher(name)).matches()) {
              if (!endTag) {
                int number = matcher.group(1).charAt(0) - '0';
                result.append("#".repeat(number)).append(' ');
                if (i + 1 < length && html.charAt(i + 1) == '\n') i++;
              }
            }
            else {
              result.append(html, tag, i + 1);
            }
          }
          else {
            result.append(html, tag, i + 1);
          }
          tag = -1;
          endTag = false;
        }
        else {
          if (context == Context.PRE) {
            if (c == '<' && match(html, "</pre>", i)) tag = i;
            else result.append(c);
          }
          else if (c == '\u0000') { // NUL marks part where text should be taken raw
            int end = html.indexOf('\u0000', i + 1);
            result.append(html, i + 1, end);
            i = end;
          }
          else if (c == '\n') {
            if (newLine && !(i + 2 < length && html.charAt(i + 2) == '@')) {
              continue;
            }
            result.append(context == Context.LIST ? "\n     " : "\n");
            newLine = true;
          }
          else {
            if (newLine && context == Context.LIST && c == ' ') continue;
            newLine = false;
            if (c == '<') tag = i;
            else result.append(c);
          }
        }
      }
      return result.toString();
    }

    private static boolean match(String s, String pattern, int offset) {
      int length = pattern.length();
      if (s.length() < offset + length) return false;
      for (int i = 0; i < length; i++) {
        if (!StringUtil.charsEqualIgnoreCase(s.charAt(offset + i), pattern.charAt(i))) return false;
      }
      return true;
    }

    private static boolean isLetterOrDigitAscii(char cur) {
      return cur >= 'a' && cur <= 'z' || cur >= 'A' && cur <= 'Z' || cur >= '0' && cur <= '9' || cur == ' ';
    }

    private static String getElementIndent(PsiElement element) {
      PsiElement leaf = PsiTreeUtil.prevLeaf(element);
      if (!(leaf instanceof PsiWhiteSpace)) {
        return "";
      }
      String text = leaf.getText();
      final int lineBreak = text.lastIndexOf('\n');
      return text.substring(lineBreak + 1);
    }

    private static void handleInlineDocTag(PsiElement element, StringBuilder result) {
      PsiElement[] children = element.getChildren();
      if (children.length > 0) {
        for (@NotNull PsiElement child : children) {
          handleInlineDocTag(child, result);
        }
      }
      else if (element instanceof PsiWhiteSpace) {
        String text = element.getText();
        if (text.contains("\n")) result.append("\n ");
        else result.append(text);
      }
      else if (!isDocToken(element, JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS)) {
        result.append(element.getText());
      }
    }

    private static void handleCode(PsiInlineDocTag inlineDocTag, StringBuilder result) {
      result.append("`\u0000");
      for (PsiElement dataElement : inlineDocTag.getDataElements()) {
        if (dataElement instanceof PsiDocToken) result.append(dataElement.getText().trim());
      }
      result.append("\u0000`");
    }

    private static void handleLink(PsiInlineDocTag inlineDocTag, StringBuilder result) {
      result.append('[');
      PsiElement[] dataElements = inlineDocTag.getDataElements();
      boolean dataFound = false;
      for (PsiElement dataElement : dataElements) {
        if (dataElement instanceof PsiDocToken) {
          String text = dataElement.getText().trim();
          if (!text.isEmpty()) {
            result.append(text);
            dataFound = true;
          }
        }
      }
      if (dataFound) result.append("][");
      for (PsiElement dataElement : dataElements) {
        if (dataElement instanceof PsiDocMethodOrFieldRef) {
          for (@NotNull PsiElement refChild : dataElement.getChildren()) {
            if (refChild instanceof PsiDocToken) {
              result.append(refChild.getText());
            }
            else if (refChild instanceof PsiDocTagValue) {
              for (@NotNull PsiElement valueChild : refChild.getChildren()) {
                if (valueChild instanceof PsiWhiteSpace) {
                  if (valueChild.getText().contains("\n")) result.append("\n ");
                  continue;
                }
                if (isDocToken(valueChild, JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS)) continue;
                result.append(valueChild.getText());
              }
            }
          }
        }
        else if (!(dataElement instanceof PsiDocToken) && !(dataElement instanceof PsiWhiteSpace)) {
          result.append(dataElement.getText());
        }
      }
      result.append(']');
    }
  }
}
