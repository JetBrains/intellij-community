// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

class LightEditTabs extends JBEditorTabs {
  LightEditTabs(@NotNull Disposable parent) {
    super(null, ActionManager.getInstance(), null, parent);
  }

  void addEditorTab(@NotNull Editor editor, @NotNull String title) {
    TabInfo tabInfo = new TabInfo(createEditorContainer(editor))
      .setText(title)
      .setTabColor(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground());
    addTabSilently(tabInfo, -1);
  }

  private static JComponent createEditorContainer(@NotNull Editor editor) {
    JPanel editorPanel = new JPanel(new BorderLayout());
    editorPanel.add(editor.getComponent(), BorderLayout.CENTER);
    return editorPanel;
  }

}
