// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.largeFilesEditor.encoding;

import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.ide.lightEdit.LightEditorInfo;
import com.intellij.ide.lightEdit.LightEditorListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LightEditLargeFileEncodingWidget extends LargeFileEncodingWidget implements LightEditorListener {

  public static final String WIDGET_ID = "light.edit.large.file.encoding.widget";

  public LightEditLargeFileEncodingWidget(@NotNull Project project) {
    super(project);
  }

  @Override
  public @NotNull String ID() {
    return WIDGET_ID;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    super.install(statusBar);
    LightEditService.getInstance().getEditorManager().addListener(this);
  }

  @Override
  public @NotNull StatusBarWidget copy() {
    return new LightEditLargeFileEncodingWidget(getProject());
  }

  @Override
  public void afterCreate(@NotNull LightEditorInfo editorInfo) {
    update();
  }

  @Override
  public void afterSelect(@Nullable LightEditorInfo editorInfo) {
    update();
  }
}
