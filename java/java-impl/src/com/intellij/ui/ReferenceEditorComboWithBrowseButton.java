// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author ven
 */
public class ReferenceEditorComboWithBrowseButton extends ComponentWithBrowseButton<EditorComboBox> implements TextAccessor {
  public ReferenceEditorComboWithBrowseButton(final ActionListener browseActionListener,
                                              final String text,
                                              @NotNull final Project project,
                                              boolean toAcceptClasses, final String recentsKey) {
    this(browseActionListener, text, project, toAcceptClasses, JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE, recentsKey);
  }

  public ReferenceEditorComboWithBrowseButton(final ActionListener browseActionListener,
                                              final String text,
                                              @NotNull final Project project,
                                              boolean toAcceptClasses,
                                              final JavaCodeFragment.VisibilityChecker visibilityChecker, final String recentsKey) {
    super(new EditorComboBox(JavaReferenceEditorUtil.createDocument(StringUtil.isEmpty(text) ? "" : text, project, toAcceptClasses, visibilityChecker), project, StdFileTypes.JAVA),
          browseActionListener);
    final List<String> recentEntries = RecentsManager.getInstance(project).getRecentEntries(recentsKey);
    if (recentEntries != null) {
      setHistory(ArrayUtil.toStringArray(recentEntries));
    }
    if (text != null && text.length() > 0) {
      prependItem(text);
    }
    else if (text != null) {
      getChildComponent().setSelectedItem(null);
    }
  }

  @Override
  public String getText(){
    return getChildComponent().getText().trim();
  }

  @Override
  public void setText(final String text){
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

  public void appendItem(String item) {
    getChildComponent().appendItem(item);
  }
}
