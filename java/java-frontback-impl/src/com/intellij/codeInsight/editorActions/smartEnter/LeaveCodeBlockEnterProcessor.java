// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.ide.DataManager;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.impl.source.BasicJavaTokenSet;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.impl.source.BasicJavaElementType.*;

public class LeaveCodeBlockEnterProcessor implements ASTNodeEnterProcessor {
  private final BasicJavaTokenSet CONTROL_FLOW_ELEMENT_TYPES =
    BasicJavaTokenSet.create(BASIC_IF_STATEMENT, BASIC_WHILE_STATEMENT, BASIC_DO_WHILE_STATEMENT, BASIC_FOR_STATEMENT,
                             BASIC_FOREACH_STATEMENT);


  @Override
  public boolean doEnter(@NotNull Editor editor, @NotNull ASTNode astNode, boolean isModified) {
    ASTNode parent = astNode.getTreeParent();
    if (!(BasicJavaAstTreeUtil.is(parent, BASIC_CODE_BLOCK))) {
      return false;
    }

    if (CONTROL_FLOW_ELEMENT_TYPES.contains(astNode.getElementType())) {
      return false;
    }

    boolean leaveCodeBlock = isControlFlowBreak(astNode);
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
   */
  private static boolean isControlFlowBreak(@Nullable ASTNode element) {
    return BasicJavaAstTreeUtil.is(element, BASIC_RETURN_STATEMENT) ||
           BasicJavaAstTreeUtil.is(element, BASIC_THROW_STATEMENT);
  }
}
