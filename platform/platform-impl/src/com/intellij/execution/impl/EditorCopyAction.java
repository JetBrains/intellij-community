/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;

/**
 * @author peter
 */
public class EditorCopyAction extends DumbAwareAction {

  @Override
  public void update(AnActionEvent e) {
    final boolean enabled = isEnabled(e);
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);

    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    e.getPresentation().setText(editor != null && editor.getSelectionModel().hasSelection()
                                ? ExecutionBundle.message("copy.selected.content.action.name")
                                : ExecutionBundle.message("copy.content.action.name"));
  }

  protected boolean isEnabled(AnActionEvent e) {
    return e.getData(PlatformDataKeys.EDITOR) != null;
  }

  public void actionPerformed(final AnActionEvent e) {
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    assert editor != null;
    if (editor.getSelectionModel().hasSelection()) {
      editor.getSelectionModel().copySelectionToClipboard();
    }
    else {
      editor.getSelectionModel().setSelection(0, editor.getDocument().getTextLength());
      editor.getSelectionModel().copySelectionToClipboard();
      editor.getSelectionModel().removeSelection();
    }
  }
}

