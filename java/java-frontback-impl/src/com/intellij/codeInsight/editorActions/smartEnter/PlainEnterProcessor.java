// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.ide.DataManager;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.impl.source.BasicJavaElementType.*;

public class PlainEnterProcessor implements ASTNodeEnterProcessor {

  @Override
  public boolean doEnter(@NotNull Editor editor, @NotNull ASTNode astNode, boolean isModified) {
    if (expandCodeBlock(editor, astNode)) return true;

    getEnterHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE).execute(editor, editor.getCaretModel().getCurrentCaret(),
                                                                     EditorUtil.getEditorDataContext(editor));
    return true;
  }

  public static boolean expandCodeBlock(@NotNull Editor editor, @Nullable ASTNode astNode) {
    ASTNode block = getControlStatementBlock(editor.getCaretModel().getOffset(), astNode);
    PsiElement psiBlock = BasicJavaAstTreeUtil.toPsi(block);
    if (processExistingBlankLine(editor, psiBlock, astNode)) {
      return true;
    }
    if (block == null) {
      return false;
    }

    EditorActionHandler enterHandler = getEnterHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE);
    ASTNode firstElement = BasicJavaAstTreeUtil.getFirstBodyElement(block);
    if (firstElement == null) {
      firstElement = BasicJavaAstTreeUtil.getRBrace(block);
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
    enterHandler.execute(editor, editor.getCaretModel().getCurrentCaret(), EditorUtil.getEditorDataContext(editor));
    return true;
  }

  private static EditorActionHandler getEnterHandler(String actionId) {
    return EditorActionManager.getInstance().getActionHandler(actionId);
  }

  private static @Nullable ASTNode getControlStatementBlock(int caret, ASTNode astNode) {
    if (BasicJavaAstTreeUtil.is(astNode, BASIC_TRY_STATEMENT)) {
      ASTNode tryBlock = BasicJavaAstTreeUtil.getCodeBlock(astNode);
      if (tryBlock != null && caret < tryBlock.getTextRange().getEndOffset()) return tryBlock;

      for (ASTNode catchBlock : BasicJavaAstTreeUtil.getCatchBlocks(astNode)) {
        if (catchBlock != null && caret < catchBlock.getTextRange().getEndOffset()) return catchBlock;
      }

      return BasicJavaAstTreeUtil.getFinallyBlock(astNode);
    }

    if (BasicJavaAstTreeUtil.is(astNode, BASIC_SYNCHRONIZED_STATEMENT)) {
      return BasicJavaAstTreeUtil.getCodeBlock(astNode);
    }

    if (BasicJavaAstTreeUtil.is(astNode, BASIC_METHOD)) {
      ASTNode methodBody = BasicJavaAstTreeUtil.getCodeBlock(astNode);
      if (methodBody != null) return methodBody;
    }

    if (BasicJavaAstTreeUtil.is(astNode, BASIC_SWITCH_STATEMENT)) {
      return BasicJavaAstTreeUtil.getCodeBlock(astNode);
    }

    ASTNode body = null;
    if (BasicJavaAstTreeUtil.is(astNode, BASIC_IF_STATEMENT)) {
      body = BasicJavaAstTreeUtil.getThenBranch(astNode);
      if (body != null && caret > body.getTextRange().getEndOffset()) {
        body = BasicJavaAstTreeUtil.getElseBranch(astNode);
      }
    }
    else if (BasicJavaAstTreeUtil.is(astNode, BASIC_WHILE_STATEMENT)) {
      body = BasicJavaAstTreeUtil.getBlock(astNode);
    }
    else if (BasicJavaAstTreeUtil.is(astNode, BASIC_FOR_STATEMENT)) {
      body = BasicJavaAstTreeUtil.getForBody(astNode);
    }
    else if (BasicJavaAstTreeUtil.is(astNode, BASIC_FOREACH_STATEMENT)) {
      body = BasicJavaAstTreeUtil.getBlock(astNode);
    }
    else if (BasicJavaAstTreeUtil.is(astNode, BASIC_DO_WHILE_STATEMENT)) {
      body = BasicJavaAstTreeUtil.getDoWhileBody(astNode);
    }

    return BasicJavaAstTreeUtil.is(body, BASIC_BLOCK_STATEMENT) ?
           BasicJavaAstTreeUtil.getCodeBlock(body) : null;
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
   * @param editor    target editor
   * @param codeBlock target code block to which new empty line is going to be inserted
   * @param element   target element under caret
   * @return {@code true} if it was found out that the given code block starts with the empty line and caret
   * is pointed to correct position there, i.e. no additional processing is required;
   * {@code false} otherwise
   */
  private static boolean processExistingBlankLine(@NotNull Editor editor, @Nullable PsiElement codeBlock, @Nullable ASTNode element) {
    PsiWhiteSpace whiteSpace = null;
    if (codeBlock == null) {
      PsiElement psiElement = BasicJavaAstTreeUtil.toPsi(element);
      if (psiElement != null && !(BasicJavaAstTreeUtil.is(element, MEMBER_SET))) {
        final PsiElement next = PsiTreeUtil.nextLeaf(psiElement);
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
    actionHandler.execute(editor, editor.getCaretModel().getCurrentCaret(), dataContext);
    return true;
  }
}
