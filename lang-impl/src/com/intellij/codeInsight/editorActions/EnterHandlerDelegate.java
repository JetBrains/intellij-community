package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public interface EnterHandlerDelegate {
  ExtensionPointName<EnterHandlerDelegate> EP_NAME = ExtensionPointName.create("com.intellij.enterHandlerDelegate");

  enum Result { Handled, NotHandled, HandledAndForceIndent }

  Result preprocessEnter(final PsiFile file, final Editor editor, final int caretOffset, final DataContext dataContext, 
                         final EditorActionHandler originalHandler);
}
