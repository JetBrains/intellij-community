// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.statusBar;

import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.ide.lightEdit.LightEditorInfo;
import com.intellij.ide.lightEdit.LightEditorInfoImpl;
import com.intellij.ide.lightEdit.LightEditorListener;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class LightEditAbstractPopupWidgetWrapper
  implements StatusBarWidget, LightEditorListener, CustomStatusBarWidget {

  private final NotNullLazyValue<EditorBasedStatusBarPopup> myOriginalInstance =
    NotNullLazyValue.createValue(() -> createOriginalWidget());

  private @Nullable      Editor  myEditor;
  private final @NotNull Project myProject;

  protected LightEditAbstractPopupWidgetWrapper(@NotNull Project project) {
    myProject = project;
  }

  protected DataContext getEditorDataContext(@NotNull DataContext originalContext) {
    return myEditor == null ? originalContext : SimpleDataContext.getSimpleContext(CommonDataKeys.EDITOR, myEditor, originalContext);
  }

  @Nullable
  protected Editor getLightEditor() {
    return myEditor;
  }

  @NotNull
  protected abstract EditorBasedStatusBarPopup createOriginalWidget();

  @NotNull
  private EditorBasedStatusBarPopup getOriginalWidget() {
    return myOriginalInstance.getValue();
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    getOriginalWidget().install(statusBar);
    LightEditService.getInstance().getEditorManager().addListener(this);
  }

  @Override
  public void dispose() {
    Disposer.dispose(getOriginalWidget());
  }

  @Override
  public void afterSelect(@Nullable LightEditorInfo editorInfo) {
    myEditor = LightEditorInfoImpl.getEditor(editorInfo);
    getOriginalWidget().setEditor(myEditor);
    getOriginalWidget().update();
  }

  @Override
  public JComponent getComponent() {
    return getOriginalWidget().getComponent();
  }

  protected @NotNull Project getProject() {
    return myProject;
  }
}
