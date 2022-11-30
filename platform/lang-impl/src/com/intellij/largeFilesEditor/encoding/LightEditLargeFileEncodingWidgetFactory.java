// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.largeFilesEditor.encoding;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.largeFilesEditor.editor.LargeFileEditor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory;
import org.jetbrains.annotations.NotNull;

final class LightEditLargeFileEncodingWidgetFactory extends StatusBarEditorBasedWidgetFactory implements LightEditCompatible {
  @Override
  public @NotNull String getId() {
    return LightEditLargeFileEncodingWidget.WIDGET_ID;
  }

  @Override
  public @NotNull String getDisplayName() {
    return EditorBundle.message("large.file.editor.encoding.widget.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project) {
    return LightEdit.owns(project);
  }

  @Override
  public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
    return getFileEditor(statusBar) instanceof LargeFileEditor && LightEdit.owns(statusBar.getProject());
  }

  @Override
  public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
    return new LightEditLargeFileEncodingWidget(project);
  }

  @Override
  public boolean isConfigurable() {
    return false;
  }
}
