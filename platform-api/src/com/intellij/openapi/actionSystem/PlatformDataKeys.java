package com.intellij.openapi.actionSystem;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;

/**
 * @author yole
 */
@SuppressWarnings({"deprecation"})
public final class PlatformDataKeys {
  private PlatformDataKeys() {
  }

  public static final DataKey<Project> PROJECT = DataKey.create(DataConstants.PROJECT);
  public static final DataKey<Editor> EDITOR = DataKey.create(DataConstants.EDITOR);
}