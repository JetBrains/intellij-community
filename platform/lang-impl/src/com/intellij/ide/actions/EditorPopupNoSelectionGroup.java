// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

public class EditorPopupNoSelectionGroup extends DefaultActionGroup {
  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    e.getPresentation().setEnabledAndVisible(editor == null || !editor.getSelectionModel().hasSelection());
  }
}
