package com.intellij.codeInsight.editorActions.enter;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public interface EnterHandlerDelegate {
  ExtensionPointName<EnterHandlerDelegate> EP_NAME = ExtensionPointName.create("com.intellij.enterHandlerDelegate");

  enum Result {
    Default, Continue, DefaultForceIndent, Stop
  }

  Result preprocessEnter(final PsiFile file, final Editor editor, final Ref<Integer> caretOffset, final Ref<Integer> caretAdvance, 
                         final DataContext dataContext, final EditorActionHandler originalHandler);
}
