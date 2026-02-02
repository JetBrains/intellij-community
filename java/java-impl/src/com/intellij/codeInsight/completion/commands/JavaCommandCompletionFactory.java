// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands;

import com.intellij.codeInsight.completion.command.CommandCompletionFactory;
import com.intellij.codeInsight.completion.command.commands.IntentionCommandOffsetProvider;
import com.intellij.codeInsight.completion.command.commands.IntentionCommandSkipper;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateGetterOrSetterFix;
import com.intellij.codeInsight.daemon.impl.quickfix.ExpensivePsiIntentionAction;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandService;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJShellFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiTypeElement;
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

class JavaCommandCompletionFactory implements CommandCompletionFactory, DumbAware {

  @Override
  public boolean isApplicable(@NotNull PsiFile psiFile, int offset) {
    if (!(psiFile instanceof PsiJavaFile)) return false;
    //Doesn't work well. Disable for now
    if (psiFile instanceof PsiJShellFile) return false;
    if (isInsideParameterList(psiFile, offset)) return false;
    if (isInsideStringLiteral(psiFile, offset)) return false;
    return true;
  }

  private static boolean isInsideStringLiteral(@NotNull PsiFile file, int offset) {
    PsiElement elementAt = file.findElementAt(offset);
    if (!(elementAt instanceof PsiJavaToken psiJavaToken &&
          (psiJavaToken.getTokenType() == JavaTokenType.STRING_LITERAL ||
           psiJavaToken.getTokenType() == JavaTokenType.TEXT_BLOCK_LITERAL
          ))) {
      return false;
    }

    return psiJavaToken.getTextRange().containsOffset(offset);
  }

  private static boolean isInsideParameterList(@NotNull PsiFile psiFile, int offset) {
    PsiElement elementAt = psiFile.findElementAt(offset);
    if (elementAt == null) return false;
    if (!(elementAt.getParent() instanceof PsiParameterList)) return false;
    PsiElement prevLeaf = PsiTreeUtil.prevLeaf(elementAt, true);
    if (!(prevLeaf instanceof PsiJavaToken javaToken && javaToken.textMatches("."))) return false;
    PsiElement prevPrevLeaf = PsiTreeUtil.prevLeaf(prevLeaf, true);
    return PsiTreeUtil.getParentOfType(prevPrevLeaf, PsiTypeElement.class) != null;
  }

  static class JavaIntentionCommandSkipper implements IntentionCommandSkipper {
    @Override
    public boolean skip(@NotNull CommonIntentionAction action, @NotNull PsiFile psiFile, int offset) {
      if (action instanceof ExpensivePsiIntentionAction) return true;
      LocalQuickFix fix = QuickFixWrapper.unwrap(action);
      if (fix != null) {
        ModCommandAction unwrappedAction = ModCommandService.getInstance().unwrap(fix);
        if (unwrappedAction instanceof CreateGetterOrSetterFix) return true;
      }
      return IntentionCommandSkipper.super.skip(action, psiFile, offset);
    }
  }

  static class JavaIntentionCommandOffsetProvider implements IntentionCommandOffsetProvider {
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
}
