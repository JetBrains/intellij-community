// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.widget;

import com.intellij.json.JsonBundle;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory;
import org.jetbrains.annotations.NotNull;

public class JsonSchemaStatusWidgetFactory extends StatusBarEditorBasedWidgetFactory {
  @Override
  public @NotNull String getId() {
    return JsonSchemaStatusWidget.ID;
  }

  @Override
  public @NotNull String getDisplayName() {
    return JsonBundle.message("schema.widget.display.name");
  }

  @Override
  public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
    Project project = statusBar.getProject();
    if (project == null) {
      return false;
    }

    FileEditor editor = getFileEditor(statusBar);
    return JsonSchemaStatusWidget.isAvailableOnFile(project, editor != null ? editor.getFile() : null);
  }

  @Override
  public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
    return new JsonSchemaStatusWidget(project);
  }

  @Override
  public void disposeWidget(@NotNull StatusBarWidget widget) {
    Disposer.dispose(widget);
  }
}
