// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionListener;
import java.util.List;

/**
 * @deprecated Not needed and not used in IJ project
 */
@Deprecated
public class EditorComboWithBrowseButton extends ComponentWithBrowseButton<EditorComboBox> implements TextAccessor {
  public EditorComboWithBrowseButton(final ActionListener browseActionListener,
                                     final String text,
                                     final @NotNull Project project,
                                     final String recentsKey) {
    super(new EditorComboBox(text, project, StdFileTypes.PLAIN_TEXT), browseActionListener);
    final List<String> recentEntries = RecentsManager.getInstance(project).getRecentEntries(recentsKey);
    if (recentEntries != null) {
      setHistory(ArrayUtilRt.toStringArray(recentEntries));
    }
    if (text != null && text.length() > 0) {
      prependItem(text);
    }
  }

  @Override
  public String getText() {
    return getChildComponent().getText().trim();
  }

  @Override
  public void setText(final String text) {
    getChildComponent().setText(text);
  }

  public boolean isEditable() {
    return !getChildComponent().getEditorEx().isViewer();
  }

  public void setHistory(String[] history) {
    getChildComponent().setHistory(history);
  }

  public void prependItem(String item) {
    getChildComponent().prependItem(item);
  }
}