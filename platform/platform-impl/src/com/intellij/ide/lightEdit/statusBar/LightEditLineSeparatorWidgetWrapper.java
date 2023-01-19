// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.statusBar;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup;
import com.intellij.openapi.wm.impl.status.LineSeparatorPanel;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LightEditLineSeparatorWidgetWrapper extends LightEditAbstractPopupWidgetWrapper {
  public static final String WIDGET_ID = "light.edit.line.separator.widget";

  public LightEditLineSeparatorWidgetWrapper(@NotNull Project project, @NotNull CoroutineScope scope) {
    super(project, scope);
  }

  @Override
  public @NotNull String ID() {
    return WIDGET_ID;
  }

  @Override
  protected @NotNull EditorBasedStatusBarPopup createOriginalWidget(@NotNull CoroutineScope scope) {
    return new LineSeparatorPanel(getProject(), scope) {
      @Override
      protected @Nullable Editor getEditor() {
        return getLightEditor();
      }
    };
  }
}
