package com.intellij.openapi.actionSystem;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;

/**
 * @author yole
 */
@SuppressWarnings({"deprecation"})
public final class PlatformDataKeys {
  private PlatformDataKeys() {
  }

  public static final DataKey<Project> PROJECT = DataKey.create(DataConstants.PROJECT);
  public static final DataKey<Editor> EDITOR = DataKey.create(DataConstants.EDITOR);
  public static final DataKey<Navigatable> NAVIGATABLE = DataKey.create(DataConstants.NAVIGATABLE);
  public static final DataKey<Navigatable[]> NAVIGATABLE_ARRAY = DataKey.create(DataConstants.NAVIGATABLE_ARRAY);
}