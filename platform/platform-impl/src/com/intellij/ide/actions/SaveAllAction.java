/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
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
    ApplicationManager.getApplication().saveAll();
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
