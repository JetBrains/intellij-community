package com.intellij.codeInsight.daemon;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface ReferenceImporter {
  ExtensionPointName<ReferenceImporter> EP_NAME = ExtensionPointName.create("com.intellij.referenceImporter");

  boolean autoImportReferenceAtCursor(@NotNull Editor editor, @NotNull PsiFile file);
}
