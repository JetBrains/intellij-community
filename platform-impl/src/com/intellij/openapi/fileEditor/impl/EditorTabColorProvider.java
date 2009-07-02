package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author spleaner
 */
public interface EditorTabColorProvider {
  ExtensionPointName<EditorTabColorProvider> EP_NAME = ExtensionPointName.create("com.intellij.editorTabColorProvider"); 

  @Nullable
  Color getEditorTabColor(Project project, VirtualFile file);
}
