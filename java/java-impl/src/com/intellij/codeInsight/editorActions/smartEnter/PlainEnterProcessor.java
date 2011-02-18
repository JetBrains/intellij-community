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

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 10:54:59 PM
 * To change this template use Options | File Templates.
 */
public class PlainEnterProcessor implements EnterProcessor {
  public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
    PsiCodeBlock block = getControlStatementBlock(editor.getCaretModel().getOffset(), psiElement);
    EditorActionHandler enterHandler = getEnterHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE);
    if (block != null) {
      PsiElement firstElement = block.getFirstBodyElement();
      if (firstElement == null) {
        firstElement = block.getRBrace();
        // Plain enter processor inserts enter after the end of line, hence, we don't want to use it here because the line ends with 
        // the empty braces block. So, we get the following in case of default handler usage:
        //     Before:
        //         if (condition[caret]) {}
        //     After:
        //         if (condition) {}
        //             [caret]
        enterHandler = getEnterHandler(IdeActions.ACTION_EDITOR_ENTER);
      }
      editor.getCaretModel().moveToOffset(firstElement != null ?
                                          firstElement.getTextRange().getStartOffset() :
                                          block.getTextRange().getEndOffset());
    }

    enterHandler.execute(editor, ((EditorEx)editor).getDataContext());
    return true;
  }

  private static EditorActionHandler getEnterHandler(String actionId) {
    return EditorActionManager.getInstance().getActionHandler(actionId);
  }

  @Nullable
  private static PsiCodeBlock getControlStatementBlock(int caret, PsiElement element) {
    PsiStatement body = null;
    if (element instanceof PsiIfStatement) {
      body =  ((PsiIfStatement)element).getThenBranch();
      if (body != null && caret > body.getTextRange().getEndOffset()) {
        body = ((PsiIfStatement)element).getElseBranch();
      }
    }
    else if (element instanceof PsiWhileStatement) {
      body =  ((PsiWhileStatement)element).getBody();
    }
    else if (element instanceof PsiForStatement) {
      body =  ((PsiForStatement)element).getBody();
    }
    else if (element instanceof PsiForeachStatement) {
      body =  ((PsiForeachStatement)element).getBody();
    }
    else if (element instanceof PsiDoWhileStatement) {
      body =  ((PsiDoWhileStatement)element).getBody();
    }
    else if (element instanceof PsiMethod) {
      PsiCodeBlock methodBody = ((PsiMethod)element).getBody();
      if (methodBody != null) return methodBody;
    }

    return body instanceof PsiBlockStatement ? ((PsiBlockStatement)body).getCodeBlock() : null;
  }
}
