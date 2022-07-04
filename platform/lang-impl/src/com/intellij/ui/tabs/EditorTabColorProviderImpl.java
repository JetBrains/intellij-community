// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui.tabs;

import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.FileColorManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author spleaner
 */
public class EditorTabColorProviderImpl implements EditorTabColorProvider, DumbAware {

  @Override
  @Nullable
  public Color getEditorTabColor(@NotNull Project project, @NotNull VirtualFile file) {
    FileColorManager colorManager = FileColorManager.getInstance(project);
    return colorManager.isEnabledForTabs() ? colorManager.getFileColor(file) : null;
  }

  @Override
  public @Nullable TextAttributes getEditorTabTextAttributes(@NotNull Project project, @NotNull VirtualFile file) {
    FileEditorManager manger = FileEditorManager.getInstance(project);
    if (!(manger instanceof FileEditorManagerImpl)) return null;
    FileEditorManagerImpl fileEditorManager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);
    TextAttributes attributes = new TextAttributes(null, null, null, EffectType.BOXED, Font.PLAIN);
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getSchemeForCurrentUITheme();
    TextAttributes errorAttributes = fileEditorManager.isProblem(file) ? scheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES) : null;
    if (errorAttributes != null) {
      attributes.copyFrom(errorAttributes);
    }
    attributes.setForegroundColor(fileEditorManager.getFileColor(file));
    return attributes;
  }

  @Nullable
  @Override
  public Color getProjectViewColor(@NotNull Project project, @NotNull VirtualFile file) {
    FileColorManager colorManager = FileColorManager.getInstance(project);
    return colorManager.isEnabledForProjectView() ? colorManager.getFileColor(file) : null;
  }
}
