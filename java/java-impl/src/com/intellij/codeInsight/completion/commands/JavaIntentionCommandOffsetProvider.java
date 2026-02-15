// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands;

import com.intellij.codeInsight.completion.command.commands.IntentionCommandOffsetProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

class JavaIntentionCommandOffsetProvider implements IntentionCommandOffsetProvider {
  @Override
  public @NotNull List<@NotNull Integer> findOffsets(@NotNull PsiFile psiFile, int offset) {
    Set<Integer> results = new HashSet<>();
    Document document = psiFile.getFileDocument();
    Queue<Integer> queue = new ArrayDeque<>();
    queue.add(offset);
    while (!queue.isEmpty()) {
      Integer currentOffset = queue.poll();
      if (currentOffset == 0) continue;
      PsiElement element = psiFile.findElementAt(currentOffset - 1);
      int currentLine = document.getLineNumber(currentOffset - 1);
      while (element instanceof PsiWhiteSpace ||
             element != null &&
             StringUtil.isEmptyOrSpaces(element.getText())) {
        element = element.getPrevSibling();
        if (element == null) {
          break;
        }
        element = PsiTreeUtil.getDeepestLast(element);
        if (currentLine != document.getLineNumber(element.getTextRange().getEndOffset())) {
          element = null;
          break;
        }
        currentOffset = element.getTextRange().getEndOffset();
      }
      results.add(currentOffset);
      if (element == null) continue;
      if (element.getParent() instanceof PsiLiteralExpression literalExpression && literalExpression.getValue() instanceof String) {
        results.add(literalExpression.getTextRange().getEndOffset() - (literalExpression.isTextBlock() ? 3 : 1));
      }
      PsiElement parent = element.getParent();
      if (element instanceof PsiJavaToken) {
        Character open = braces.get(element.getText().charAt(0));
        //collect first from last
        PsiElement curParent = parent;
        if (open != null && curParent.getTextRange().getEndOffset() == element.getTextRange().getEndOffset()) {
          while (curParent.getTextRange().getEndOffset() == element.getTextRange().getEndOffset()) {
            int nextOffset = curParent.getTextRange().getStartOffset();
            curParent = curParent.getParent();
            boolean stopParent = curParent == null || curParent instanceof PsiClass || curParent instanceof PsiFile;
            if (results.add(nextOffset) && !stopParent) {
              queue.add(nextOffset);
            }
            if (stopParent) {
              break;
            }
          }
        }
        //collect open from closed
        if (open != null) {
          for (PsiElement child : parent.getChildren()) {
            if (child instanceof PsiJavaToken && child.getText().charAt(0) == open) {
              int nextOffset = child.getTextRange().getStartOffset();
              if (!results.contains(nextOffset)) {
                queue.add(nextOffset);
              }
              break;
            }
          }
        }
      }
    }
    return new ArrayList<>(results);
  }

  private static final Map<Character, Character> braces =
    Map.of(']', '[',
           '}', '{',
           ')', '(');
}
