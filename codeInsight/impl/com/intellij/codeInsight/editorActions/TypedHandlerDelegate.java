package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public abstract class TypedHandlerDelegate {
  public static final ExtensionPointName<TypedHandlerDelegate> EP_NAME = ExtensionPointName.create("com.intellij.typedHandler");

  /**
   * If the specified character triggers auto-popup, schedules the auto-popup appearance. This method is called even
   * in overwrite mode, when the rest of typed handler delegate methods are not called.
   *
   * @param charTyped
   * @param project
   * @param editor
   * @param file
   */
  public void checkAutoPopup(char charTyped, final Project project, final Editor editor, final PsiFile file) {
  }

  /**
   * Called before the specified character typed by the user is inserted in the editor.
   *
   * @param c
   * @param project
   * @param editor
   * @param file
   * @param fileType
   * @return true if the typing has been processed - in this case, no further delegates are called and the character is not inserted.
   *         false otherwise.
   */
  public boolean beforeCharTyped(char c, final Project project, final Editor editor, final PsiFile file, final FileType fileType) {
    return false;
  }

  /**
   * Called after the specified character typed by the user has been inserted in the editor.
   *  
   * @param c
   * @param project
   * @param editor
   * @param file
   */
  public boolean charTyped(char c, final Project project, final Editor editor, final PsiFile file) {
    return false;
  }
}
