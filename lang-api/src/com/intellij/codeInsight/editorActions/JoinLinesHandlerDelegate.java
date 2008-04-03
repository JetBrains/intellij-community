package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public interface JoinLinesHandlerDelegate {
  ExtensionPointName<JoinLinesHandlerDelegate> EP_NAME = ExtensionPointName.create("com.intellij.joinLinesHandler");
  
  /**
   * Tries to join lines at the specified position of the specified file.
   *
   * @param document
   * @param file
   * @param start
   * @param end
   * @return the position to place the caret after the operation, or -1 if this handler was not able
   *         to perform the operation.
   */
  int tryJoinLines(Document document, PsiFile file, int start, final int end);
}
