package com.intellij.ide.actions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface SaveFileAsTemplateHandler {
  ExtensionPointName<SaveFileAsTemplateHandler> EP_NAME = ExtensionPointName.create("com.intellij.saveFileAsTemplateHandler");

  @Nullable
  String getTemplateText(PsiFile file, String fileText, String nameWithoutExtension);
}
