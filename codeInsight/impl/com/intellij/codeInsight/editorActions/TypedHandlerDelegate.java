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
  
  public boolean beforeCharTyped(char c, final Project project, final Editor editor, final PsiFile file, final FileType fileType) {
    return false;
  }

  public void charTyped(char c, final Project project, final Editor editor, final PsiFile file) {
  }
}
