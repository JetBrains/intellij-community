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
  public Result checkAutoPopup(char charTyped, final Project project, final Editor editor, final PsiFile file) {
    return Result.CONTINUE;
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
  public Result beforeCharTyped(char c, final Project project, final Editor editor, final PsiFile file, final FileType fileType) {
    return Result.CONTINUE;
  }

  /**
   * Called after the specified character typed by the user has been inserted in the editor.
   *  
   * @param c
   * @param project
   * @param editor
   * @param file
   */
  public Result charTyped(char c, final Project project, final Editor editor, final PsiFile file) {
    return Result.CONTINUE;
  }

  public enum Result {
    STOP,
    CONTINUE,
    DEFAULT
  }
}
