// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.statusBar;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup;
import com.intellij.openapi.wm.impl.status.LineSeparatorPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightEditLineSeparatorWidgetWrapper extends LightEditAbstractPopupWidgetWrapper {
  public static final String WIDGET_ID = "light.edit.line.separator.widget";

  public LightEditLineSeparatorWidgetWrapper(@NotNull Project project) {
    super(project);
  }

  @NotNull
  @Override
  public String ID() {
    return WIDGET_ID;
  }

  @NotNull
  @Override
  protected EditorBasedStatusBarPopup createOriginalWidget() {
    return new LineSeparatorPanel(getProject()) {
      @Nullable
      @Override
      protected Editor getEditor() {
        return getLightEditor();
      }

      @NotNull
      @Override
      protected DataContext getContext() {
        return getEditorDataContext(super.getContext());
      }
    };
  }
}
