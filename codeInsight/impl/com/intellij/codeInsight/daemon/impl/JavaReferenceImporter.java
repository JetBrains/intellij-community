package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.ReferenceImporter;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class JavaReferenceImporter implements ReferenceImporter {
  public boolean autoImportReferenceAtCursor(@NotNull final Editor editor, @NotNull final PsiFile file) {
    return ShowAutoImportPass.autoImportReferenceAtCursor(editor, file, false);
  }
}
