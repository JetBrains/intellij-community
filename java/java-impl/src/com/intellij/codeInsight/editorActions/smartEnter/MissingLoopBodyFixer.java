/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MissingLoopBodyFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    PsiLoopStatement loopStatement = getLoopParent(psiElement);
    if (loopStatement == null) return;

    final Document doc = editor.getDocument();

    PsiStatement body = loopStatement.getBody();
    if (body instanceof PsiBlockStatement) return;
    if (body != null && startLine(doc, body) == startLine(doc, loopStatement)) return;

    PsiElement eltToInsertAfter;
    if (loopStatement instanceof PsiWhileStatement) {
      eltToInsertAfter = ((PsiWhileStatement)loopStatement).getRParenth();
    }
    else if (loopStatement instanceof PsiForStatement) {
      eltToInsertAfter = ((PsiForStatement)loopStatement).getRParenth();
    }
    else if (loopStatement instanceof PsiForeachStatement) {
      eltToInsertAfter = ((PsiForeachStatement)loopStatement).getRParenth();
    }
    else {
      return;
    }
    fixLoopBody(editor, processor, loopStatement, doc, body, eltToInsertAfter);
  }

  private static PsiLoopStatement getLoopParent(PsiElement element) {
    PsiLoopStatement statement = PsiTreeUtil.getParentOfType(element, PsiLoopStatement.class);
    if (statement == null) return null;
    if (statement instanceof PsiForeachStatement) {
      return isForEachApplicable((PsiForeachStatement)statement, element) ? statement : null;
    }
    if (statement instanceof PsiForStatement) {
      return isForApplicable((PsiForStatement)statement, element) ? statement : null;
    }
    if (statement instanceof PsiWhileStatement) {
      return statement;
    }
    return null;
  }

  private static boolean isForApplicable(PsiForStatement statement, PsiElement psiElement) {
    PsiStatement init = statement.getInitialization();
    PsiStatement update = statement.getUpdate();
    PsiExpression check = statement.getCondition();

    return isValidChild(init, psiElement) || isValidChild(update, psiElement) || isValidChild(check, psiElement);
  }

  private static boolean isValidChild(PsiElement ancestor, PsiElement psiElement) {
    if (ancestor != null) {
      if (PsiTreeUtil.isAncestor(ancestor, psiElement, false)) {
        if (PsiTreeUtil.hasErrorElements(ancestor)) return false;
        return true;
      }
    }

    return false;
  }

  private static boolean isForEachApplicable(PsiForeachStatement statement, PsiElement psiElement) {
    PsiExpression iterated = statement.getIteratedValue();
    PsiParameter parameter = statement.getIterationParameter();

    return PsiTreeUtil.isAncestor(iterated, psiElement, false) || PsiTreeUtil.isAncestor(parameter, psiElement, false);
  }

  private static int startLine(Document doc, PsiElement psiElement) {
    return doc.getLineNumber(psiElement.getTextRange().getStartOffset());
  }

  static void fixLoopBody(@NotNull Editor editor,
                          @NotNull JavaSmartEnterProcessor processor,
                          @NotNull PsiLoopStatement loop,
                          @NotNull Document doc,
                          @Nullable PsiStatement body,
                          @Nullable PsiElement eltToInsertAfter) {
    if (body != null && eltToInsertAfter != null) {
      if (bodyIsIndented(loop, body)) {
        int endOffset = body.getTextRange().getEndOffset();
        doc.insertString(endOffset, "\n}");
        int offset = eltToInsertAfter.getTextRange().getEndOffset();
        doc.insertString(offset, "{");
        editor.getCaretModel().moveToOffset(endOffset + "{".length());
        processor.setSkipEnter(true);
        processor.reformat(loop);
        return;
      }
    }
    String prefix = "{}";
    String text = prefix;
    if (eltToInsertAfter == null) {
      eltToInsertAfter = loop;
      text = ")" + text;
    }
    int offset = eltToInsertAfter.getTextRange().getEndOffset();
    doc.insertString(offset, text);
    editor.getCaretModel().moveToOffset(offset + text.length() - prefix.length());
  }

  private static boolean bodyIsIndented(@NotNull PsiLoopStatement loop, @NotNull PsiElement body) {
    PsiWhiteSpace beforeBody = ObjectUtils.tryCast(body.getPrevSibling(), PsiWhiteSpace.class);
    if (beforeBody == null) return false;
    PsiWhiteSpace beforeLoop = ObjectUtils.tryCast(loop.getPrevSibling(), PsiWhiteSpace.class);
    if (beforeLoop == null) return false;
    String beforeBodyText = beforeBody.getText();
    String beforeLoopText = beforeLoop.getText();
    int beforeBodyLineBreak = beforeBodyText.lastIndexOf('\n');
    if (beforeBodyLineBreak == -1) return false;
    int beforeLoopLineBreak = beforeLoopText.lastIndexOf('\n');
    if (beforeLoopLineBreak == -1) return false;
    return beforeBodyText.length() - beforeBodyLineBreak > beforeLoopText.length() - beforeLoopLineBreak;
  }
}
