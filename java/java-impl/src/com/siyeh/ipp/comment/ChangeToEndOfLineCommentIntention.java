/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ipp.comment;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.text.CharArrayUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public final class ChangeToEndOfLineCommentIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("change.to.end.of.line.comment.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("change.to.end.of.line.comment.intention.name");
  }

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new CStyleCommentPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiComment oldComment = (PsiComment)element;
    final PsiElement parent = oldComment.getParent();
    assert parent != null;
    final String commentText = oldComment.getText();
    final String text = commentText.substring(2, commentText.length() - 2);
    final String[] lines = text.split("\n");

    final int tabSize = getTabSize(oldComment);
    final int textColumn = getTextStartColumn(text, tabSize);
    final int commentColumn = getCommentStartColumn(oldComment, tabSize);
    final int column = textColumn >= 0 ? textColumn : commentColumn - textColumn + 1;
    trimLinesWithAlignment(lines, tabSize, column);

    final Project project = oldComment.getProject();
    // newline followed by space convinces formatter to indent line
    final PsiElement ws = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n ");
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final int last = lines[lines.length - 1].trim().isEmpty() ? lines.length - 2 : lines.length - 1;
    final int first = lines[0].trim().isEmpty() ? 1 : 0;
    for (int i = last; i > first; i--) {
      parent.addAfter(factory.createCommentFromText("// " + lines[i], parent), oldComment);
      if (commentColumn > 0) parent.addAfter(ws, oldComment);
    }
    String firstLine = (textColumn >= 0 ? "// " : "//") + (first >= lines.length ? "" : lines[first]);
    oldComment.replace(factory.createCommentFromText(firstLine, parent));
  }

  private static int getTabSize(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    return file == null ? 1 : Math.max(1, CodeStyle.getIndentOptions(file).TAB_SIZE);
  }

  private static int getTextStartColumn(@NotNull String text, int tabSize) {
    int column = 0;
    boolean newlineSeen = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\n') newlineSeen = true;
      else if (c == ' ' || c == '\t') {
        if (column >= 0) column = nextColumn(column, c, tabSize);
      }
      else break;
    }
    return newlineSeen ? column : -column - 1;
  }

  private static int getCommentStartColumn(@NotNull PsiComment element, int tabSize) {
    PsiFile file = element.getContainingFile();
    if (file == null) return 0;
    String text = file.getText();
    if (text == null) return 0;
    int elementOffset = element.getTextRange().getStartOffset();
    int lineStart = CharArrayUtil.shiftBackwardUntil(text, elementOffset - 1, "\n") + 1;
    int column = 0;
    for (int i = lineStart; i < elementOffset; i++) {
      column = nextColumn(column, text.charAt(i), tabSize);
    }
    return column;
  }

  private static void trimLinesWithAlignment(String @NotNull [] lines, int tabSize, int firstLineStartColumn) {
    if (lines.length == 1) {
      lines[0] = StringUtil.trimTrailing(lines[0]);
    }
    else {
      int minIndent = firstLineStartColumn;
      for (int i = 1; i < lines.length; i++) {
        final String line = lines[i];
        int column = 0;
        for (int j = 0; j < line.length(); j++) {
          char c = line.charAt(j);
          if (" \t".indexOf(c) == -1) {
            if (column < minIndent) minIndent = column;
            break;
          }
          column = nextColumn(column, c, tabSize);
          if (column >= minIndent) break;
        }
      }
      for (int i = 1; i < lines.length; i++) {
        final String line = lines[i];
        int column = 0;
        int trimOffset = 0;
        for (; trimOffset < line.length(); trimOffset++) {
          column = nextColumn(column, line.charAt(trimOffset), tabSize);
          if (column > minIndent) break;
        }
        lines[i] = StringUtil.trimTrailing(line.substring(trimOffset));
      }
    }
  }

  private static int nextColumn(int currentColumn, char c, int tabSize) {
    return c == '\t' ? ((currentColumn / tabSize) + 1) * tabSize : currentColumn + 1;
  }
}