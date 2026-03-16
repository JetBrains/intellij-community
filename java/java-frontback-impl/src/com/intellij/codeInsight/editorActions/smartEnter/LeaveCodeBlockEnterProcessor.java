// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.ide.DataManager;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.Nullable;

public class LeaveCodeBlockEnterProcessor implements EnterProcessor {

  private static final TokenSet CONTROL_FLOW_ELEMENT_TYPES = TokenSet.create(
    JavaElementType.IF_STATEMENT, JavaElementType.WHILE_STATEMENT, JavaElementType.DO_WHILE_STATEMENT, JavaElementType.FOR_STATEMENT,
    JavaElementType.FOREACH_STATEMENT
  );

  @Override
  public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
    PsiElement parent = psiElement.getParent();
    if (!(parent instanceof PsiCodeBlock)) {
      return false;
    }

    final ASTNode node = psiElement.getNode();
    if (node != null && CONTROL_FLOW_ELEMENT_TYPES.contains(node.getElementType())) {
      return false;
    }

    boolean leaveCodeBlock = isControlFlowBreak(psiElement);
    if (!leaveCodeBlock) {
      return false;
    }

    final int offset = parent.getTextRange().getEndOffset();

    // Check if there is empty line after the code block. Just move caret there in the case of the positive answer.
    final CharSequence text = editor.getDocument().getCharsSequence();
    if (offset < text.length() - 1) {
      final int i = CharArrayUtil.shiftForward(text, offset + 1, " \t");
      if (i < text.length() && text.charAt(i) == '\n') {
        editor.getCaretModel().moveToOffset(offset + 1);
        EditorActionManager actionManager = EditorActionManager.getInstance();
        EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
        final DataContext dataContext = DataManager.getInstance().getDataContext(editor.getComponent());
        actionHandler.execute(editor, editor.getCaretModel().getCurrentCaret(), dataContext);
        return true;
      }
    }

    editor.getCaretModel().moveToOffset(offset);
    return false;
  }

  /**
   * Handles situations like the one below:
   * <pre>
   *   void foo(int i) {
   *     if (i < 0) {
   *       return;[caret]
   *     }
   *   }
   * </pre>
   *
   * <b>Output:</b>
   * <pre>
   *   void foo(int i) {
   *     if (i < 0) {
   *       return;
   *     }
   *     [caret]
   *   }
   * </pre>
   *
   */
  private static boolean isControlFlowBreak(@Nullable PsiElement element) {
    return element instanceof PsiReturnStatement || element instanceof PsiThrowStatement;
  }
}
