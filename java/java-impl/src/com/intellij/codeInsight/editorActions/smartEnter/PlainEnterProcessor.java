/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.ide.DataManager;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 10:54:59 PM
 * To change this template use Options | File Templates.
 */
public class PlainEnterProcessor implements EnterProcessor {
  @Override
  public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
    if (expandCodeBlock(editor, psiElement)) return true;

    getEnterHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE).execute(editor, ((EditorEx)editor).getDataContext());
    return true;
  }

  static boolean expandCodeBlock(Editor editor, PsiElement psiElement) {
    PsiCodeBlock block = getControlStatementBlock(editor.getCaretModel().getOffset(), psiElement);
    if (processExistingBlankLine(editor, block, psiElement)) {
      return true;
    }
    if (block == null) {
      return false;
    }

    EditorActionHandler enterHandler = getEnterHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE);
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
    enterHandler.execute(editor, ((EditorEx)editor).getDataContext());
    return true;
  }

  private static EditorActionHandler getEnterHandler(String actionId) {
    return EditorActionManager.getInstance().getActionHandler(actionId);
  }

  @Nullable
  private static PsiCodeBlock getControlStatementBlock(int caret, PsiElement element) {
    if (element instanceof PsiTryStatement) {
      PsiCodeBlock tryBlock = ((PsiTryStatement)element).getTryBlock();
      if (tryBlock != null && caret < tryBlock.getTextRange().getEndOffset()) return tryBlock;

      for (PsiCodeBlock catchBlock : ((PsiTryStatement)element).getCatchBlocks()) {
        if (catchBlock != null && caret < catchBlock.getTextRange().getEndOffset()) return catchBlock;
      }

      return ((PsiTryStatement)element).getFinallyBlock();
    }

    if (element instanceof PsiMethod) {
      PsiCodeBlock methodBody = ((PsiMethod)element).getBody();
      if (methodBody != null) return methodBody;
    }

    if (element instanceof PsiSwitchStatement) {
      return ((PsiSwitchStatement)element).getBody();
    }

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

    return body instanceof PsiBlockStatement ? ((PsiBlockStatement)body).getCodeBlock() : null;
  }

  /**
   * There is a possible case that target code block already starts with the empty line:
   * <pre>
   *   void test(int i) {
   *     if (i > 1[caret]) {
   *       
   *     }
   *   }
   * </pre>
   * We want just move caret to correct position at that empty line without creating additional empty line then.
   *  
   * @param editor      target editor
   * @param codeBlock   target code block to which new empty line is going to be inserted
   * @param element     target element under caret
   * @return            <code>true</code> if it was found out that the given code block starts with the empty line and caret
   *                    is pointed to correct position there, i.e. no additional processing is required;
   *                    <code>false</code> otherwise
   */
  private static boolean processExistingBlankLine(@NotNull Editor editor, @Nullable PsiCodeBlock codeBlock, @Nullable PsiElement element) {
    PsiWhiteSpace whiteSpace = null;
    if (codeBlock == null) {
      if (element != null && !(element instanceof PsiMember)) {
        final PsiElement next = PsiTreeUtil.nextLeaf(element);
        if (next instanceof PsiWhiteSpace) {
          whiteSpace = (PsiWhiteSpace)next;
        }
      }
    }
    else {
      whiteSpace = PsiTreeUtil.findChildOfType(codeBlock, PsiWhiteSpace.class);
      if (whiteSpace == null) {
        return false;
      }

      PsiElement lbraceCandidate = whiteSpace.getPrevSibling();
      if (lbraceCandidate == null) {
        return false;
      }

      ASTNode node = lbraceCandidate.getNode();
      if (node == null || node.getElementType() != JavaTokenType.LBRACE) {
        return false;
      }
    }

    if (whiteSpace == null) {
      return false;
    }

    final TextRange textRange = whiteSpace.getTextRange();
    final Document document = editor.getDocument();
    final CharSequence whiteSpaceText = document.getCharsSequence().subSequence(textRange.getStartOffset(), textRange.getEndOffset());
    if (StringUtil.countNewLines(whiteSpaceText) < 2) {
      return false;
    }

    int i = CharArrayUtil.shiftForward(whiteSpaceText, 0, " \t");
    if (i >= whiteSpaceText.length() - 1) {
      assert false : String.format("code block: %s, white space: %s",
                                   codeBlock == null ? "undefined" : codeBlock.getTextRange(),
                                   whiteSpace.getTextRange());
      return false;
    }

    editor.getCaretModel().moveToOffset(i + 1 + textRange.getStartOffset());
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
    final DataContext dataContext = DataManager.getInstance().getDataContext(editor.getComponent());
    if (dataContext == null) {
      i = CharArrayUtil.shiftForwardUntil(whiteSpaceText, i, "\n");
      if (i >= whiteSpaceText.length()) {
        i = whiteSpaceText.length();
      }
      editor.getCaretModel().moveToOffset(i + textRange.getStartOffset());
    }
    else {
      actionHandler.execute(editor, dataContext);
    }
    return  true;
  }
}
