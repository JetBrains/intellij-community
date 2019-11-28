// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

public abstract class PluggableLafInfo extends UIManager.LookAndFeelInfo {
  public PluggableLafInfo(String name, String className) {
    super(name, className);
  }

  public static class SearchAreaContext {
    private final JComponent searchTextArea;
    private final JTextComponent textComponent;
    private final JComponent iconsPanel;
    private final JComponent scrollPane;

    public SearchAreaContext(JComponent searchTextArea, JTextComponent textComponent,
                             JComponent iconsPanel, JComponent scrollPane) {
      this.searchTextArea = searchTextArea;
      this.textComponent = textComponent;
      this.iconsPanel = iconsPanel;
      this.scrollPane = scrollPane;
    }

    public JComponent getSearchComponent() {
      return searchTextArea;
    }

    public JTextComponent getTextComponent() {
      return textComponent;
    }

    public JComponent getIconsPanel() {
      return iconsPanel;
    }

    public JComponent getScrollPane() {
      return scrollPane;
    }
  }

  public abstract SearchTextAreaPainter createSearchAreaPainter(@NotNull SearchAreaContext context);
  public abstract DarculaEditorTextFieldBorder createEditorTextFieldBorder(EditorTextField editorTextField, EditorEx editor);
}
