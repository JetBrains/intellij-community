package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface EditorTabTitleProvider {
  ExtensionPointName<EditorTabTitleProvider> EP_NAME = ExtensionPointName.create("com.intellij.editorTabTitleProvider"); 

  @Nullable
  String getEditorTabTitle(final Project project, VirtualFile file);
}
