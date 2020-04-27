// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.statusBar;

import com.intellij.ide.lightEdit.LightEditUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup;
import com.intellij.openapi.wm.impl.status.EncodingPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightEditEncodingWidgetWrapper extends LightEditAbstractPopupWidgetWrapper {
  public static final String WIDGET_ID = "light.edit.encoding.widget";

  @NotNull
  @Override
  public String ID() {
    return WIDGET_ID;
  }

  @NotNull
  @Override
  protected EditorBasedStatusBarPopup createOriginalWidget() {
    return new EncodingPanel(LightEditUtil.getProject()) {
      @Override
      protected @Nullable Editor getEditor() {
        return getLightEditor();
      }

      @Override
      protected @NotNull DataContext getContext() {
        return getEditorDataContext(super.getContext());
      }
    };
  }
}
