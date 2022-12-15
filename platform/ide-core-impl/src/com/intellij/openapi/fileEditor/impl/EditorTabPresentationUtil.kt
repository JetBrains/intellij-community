// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public final class EditorTabPresentationUtil {
  public static @NotNull @NlsContexts.TabTitle String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file) {
    String overriddenTitle = getCustomEditorTabTitle(project, file);
    if (overriddenTitle != null) {
      return overriddenTitle;
    }
    String uniqueTitle = new UniqueNameEditorTabTitleProvider().getEditorTabTitle(project, file);
    if (uniqueTitle != null) {
      return uniqueTitle;
    }
    return file.getPresentableName();
  }

  public static @Nullable @NlsContexts.TabTitle String getCustomEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file) {
    for (EditorTabTitleProvider provider : DumbService.getDumbAwareExtensions(project, EditorTabTitleProvider.EP_NAME)) {
      String result = provider.getEditorTabTitle(project, file);
      if (Strings.isNotEmpty(result)) {
        return result;
      }
    }
    return null;
  }

  public static @NotNull String getUniqueEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file) {
    String name = getEditorTabTitle(project, file);
    if (name.equals(file.getPresentableName())) {
      return UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(project, file);
    }
    return name;
  }

  public static @Nullable Color getEditorTabBackgroundColor(@NotNull Project project, @NotNull VirtualFile file) {
    for (EditorTabColorProvider provider : DumbService.getDumbAwareExtensions(project, EditorTabColorProvider.EP_NAME)) {
      Color result = provider.getEditorTabColor(project, file);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  public static @Nullable Color getFileBackgroundColor(@NotNull Project project, @NotNull VirtualFile file) {
    for (EditorTabColorProvider provider : DumbService.getDumbAwareExtensions(project, EditorTabColorProvider.EP_NAME)) {
      Color result = provider.getProjectViewColor(project, file);
      if (result != null) {
        return result;
      }
    }
    return null;
  }
}
