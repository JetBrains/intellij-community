package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public abstract class BackspaceHandlerDelegate {
  public static ExtensionPointName<BackspaceHandlerDelegate> EP_NAME = ExtensionPointName.create("com.intellij.backspaceHandlerDelegate");

  public abstract void beforeCharDeleted(char c, PsiFile file, Editor editor);
  public abstract boolean charDeleted(final char c, final PsiFile file, final Editor editor);
}
