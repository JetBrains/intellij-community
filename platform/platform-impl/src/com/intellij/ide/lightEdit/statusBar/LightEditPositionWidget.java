// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.statusBar;

import com.intellij.ide.lightEdit.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.impl.status.PositionPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightEditPositionWidget extends PositionPanel implements LightEditorListener {
  private final LightEditorManager myEditorManager;
  private @Nullable Editor myEditor;

  public LightEditPositionWidget(@NotNull Project project, @NotNull LightEditorManager editorManager) {
    super(project);
    myEditorManager = editorManager;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    super.install(statusBar);
    myEditorManager.addListener(this);
  }

  @Override
  public boolean isOurEditor(Editor editor) {
    return editor != null && myEditor == editor && editor.getComponent().isShowing();
  }

  @Override
  public void afterSelect(@Nullable LightEditorInfo editorInfo) {
    myEditor = LightEditorInfoImpl.getEditor(editorInfo);
  }

  @Nullable
  @Override
  protected Editor getEditor() {
    return myEditor;
  }
}
