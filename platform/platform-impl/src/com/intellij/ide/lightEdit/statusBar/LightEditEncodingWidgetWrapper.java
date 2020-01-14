// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.statusBar;

import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.ide.lightEdit.LightEditUtil;
import com.intellij.ide.lightEdit.LightEditorInfo;
import com.intellij.ide.lightEdit.LightEditorListener;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EncodingPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class LightEditEncodingWidgetWrapper implements StatusBarWidget, LightEditorListener, CustomStatusBarWidget {
  private final EncodingPanel myEncodingPanel;
  private @Nullable Editor myEditor;

  public LightEditEncodingWidgetWrapper() {
    myEncodingPanel = new EncodingPanel(LightEditUtil.getProject()) {
      @Nullable
      @Override
      protected Editor getEditor() {
        return myEditor;
      }

      @NotNull
      @Override
      protected DataContext getContext() {
        DataContext dataContext = super.getContext();
        return SimpleDataContext.getSimpleContext(
          CommonDataKeys.EDITOR.getName(), myEditor, dataContext
        );
      }
    };
  }

  @NotNull
  @Override
  public String ID() {
    return "light.edit.encoding.widget";
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    myEncodingPanel.install(statusBar);
    LightEditService.getInstance().getEditorManager().addListener(this);
  }

  @Override
  public void dispose() {
    Disposer.dispose(myEncodingPanel);
  }

  @Override
  public void afterSelect(@Nullable LightEditorInfo editorInfo) {
    myEditor = editorInfo != null ? editorInfo.getEditor() : null;
    myEncodingPanel.setEditor(myEditor);
    myEncodingPanel.update();
  }

  @Override
  public JComponent getComponent() {
    return myEncodingPanel.getComponent();
  }
}
