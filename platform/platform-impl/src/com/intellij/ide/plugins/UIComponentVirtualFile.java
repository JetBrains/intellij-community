// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithoutContent;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class UIComponentVirtualFile extends LightVirtualFile implements VirtualFileWithoutContent {

  private final @Nullable Icon myIcon;

  public UIComponentVirtualFile(@NotNull String name, @Nullable Icon icon) {
    super(name);
    myIcon = icon;
    putUserData(FileEditorManagerImpl.FORBID_PREVIEW_TAB, true);
  }

  @Override
  public @NotNull String getPath() {
    return getName();
  }

  public abstract @NotNull Content createContent(@NotNull UIComponentFileEditor editor);

  public interface Content {

    @NotNull JComponent createComponent();

    @Nullable JComponent getPreferredFocusedComponent();
  }

  static class UIComponentVirtualFileIconProvider implements FileIconProvider {
    @Override
    public @Nullable Icon getIcon(@NotNull VirtualFile file, int flags, @Nullable Project project) {
      if (file instanceof UIComponentVirtualFile) {
        return ((UIComponentVirtualFile)file).myIcon;
      }
      return null;
    }
  }
}
