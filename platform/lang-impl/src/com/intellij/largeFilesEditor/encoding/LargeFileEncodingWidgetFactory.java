// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.largeFilesEditor.encoding;

import com.intellij.largeFilesEditor.editor.LargeFileEditor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory;
import org.jetbrains.annotations.NotNull;

public class LargeFileEncodingWidgetFactory extends StatusBarEditorBasedWidgetFactory {
  @Override
  public @NotNull String getId() {
    return LargeFileEncodingWidget.WIDGET_ID;
  }

  @Override
  public @NotNull String getDisplayName() {
    return EditorBundle.message("large.file.editor.encoding.widget.name");
  }

  @Override
  public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
    return getFileEditor(statusBar) instanceof LargeFileEditor;
  }

  @Override
  public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
    return new LargeFileEncodingWidget(project);
  }

  @Override
  public void disposeWidget(@NotNull StatusBarWidget widget) {
    Disposer.dispose(widget);
  }

  @Override
  public boolean isConfigurable() {
    return false;
  }
}
