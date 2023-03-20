// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * Allows adjusting the text of a file being saved as a template before it is stored.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/using-file-templates.html#improving-save-file-as-template-action">Improving "Save File as Template…" Action (IntelliJ Platform Docs)</a>
 */
public interface SaveFileAsTemplateHandler {
  ExtensionPointName<SaveFileAsTemplateHandler> EP_NAME = ExtensionPointName.create("com.intellij.saveFileAsTemplateHandler");

  /**
   *
   * @param file the file being saved as a template
   * @return adjusted file text to be saved as a template
   */
  @Nullable
  String getTemplateText(PsiFile file, String fileText, String nameWithoutExtension);
}
