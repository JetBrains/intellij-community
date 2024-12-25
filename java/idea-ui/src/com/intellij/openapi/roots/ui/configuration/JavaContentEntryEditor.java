// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ExcludeFolder;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public abstract class JavaContentEntryEditor extends ContentEntryEditor {
  private final CompilerModuleExtension myCompilerExtension;

  public JavaContentEntryEditor(final String contentEntryUrl, List<ModuleSourceRootEditHandler<?>> moduleSourceRootEditHandlers) {
    super(contentEntryUrl, moduleSourceRootEditHandlers);
    myCompilerExtension = getModel().getModuleExtension(CompilerModuleExtension.class);
  }

  @Override
  protected ContentRootPanel createContentRootPane() {
    return new ContentRootPanel(this, getEditHandlers()) {
      @Override
      protected @Nullable ContentEntry getContentEntry() {
        return getThisContentEntry();
      }

      @Override
      protected @Nullable JComponent createRootPropertiesEditor(ModuleSourceRootEditHandler<?> editor, SourceFolder folder) {
        return editor.createPropertiesEditor(folder, this, myCallback);
      }
    };
  }

  private ContentEntry getThisContentEntry() {
    return getContentEntry();
  }

  @Override
  protected ExcludeFolder doAddExcludeFolder(final @NotNull VirtualFile file) {
    final boolean isCompilerOutput = isCompilerOutput(file.getUrl());
    if (isCompilerOutput) {
      myCompilerExtension.setExcludeOutput(true);
      return null;
    }
    return super.doAddExcludeFolder(file);
  }

  @Override
  protected void doRemoveExcludeFolder(final @NotNull String excludeRootUrl) {
    if (isCompilerOutput(excludeRootUrl)) {
      myCompilerExtension.setExcludeOutput(false);
    }
    super.doRemoveExcludeFolder(excludeRootUrl);
  }

  private boolean isCompilerOutput(final @NotNull String fileUrl) {
    if (fileUrl.equals(myCompilerExtension.getCompilerOutputUrl())) {
      return true;
    }

    if (fileUrl.equals(myCompilerExtension.getCompilerOutputUrlForTests())) {
      return true;
    }

    if (myCompilerExtension.isCompilerOutputPathInherited()) {
      final ProjectStructureConfigurable instance = ProjectStructureConfigurable.getInstance(getModel().getModule().getProject());
      final String compilerOutputUrl = instance.getProjectConfig().getCompilerOutputUrl();
      if (fileUrl.equals(compilerOutputUrl)) {
        return true;
      }
    }

    return false;
  }
}
