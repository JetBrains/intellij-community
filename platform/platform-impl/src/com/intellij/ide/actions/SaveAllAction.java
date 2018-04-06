// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.components.impl.stores.StoreUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.TrailingSpacesStripper;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public class SaveAllAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    if (editor != null) {
      stripSpacesFromCaretLines(editor);
    }

    StoreUtil.saveDocumentsAndProjectsAndApp(true);
  }

  private static void stripSpacesFromCaretLines(@NotNull Editor editor) {
    final EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    if (!EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE.equals(editorSettings.getStripTrailingSpaces())
        && !editorSettings.isKeepTrailingSpacesOnCaretLine()) {
      Document document = editor.getDocument();
      final boolean inChangedLinesOnly =
        EditorSettingsExternalizable.STRIP_TRAILING_SPACES_CHANGED.equals(editorSettings.getStripTrailingSpaces());
      TrailingSpacesStripper.strip(document, inChangedLinesOnly, false);
    }
  }
}
