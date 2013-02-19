/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class LookupDocumentSavingVetoer implements FileDocumentSynchronizationVetoer {
  @Override
  public boolean maySaveDocument(@NotNull Document document) {
    if (ApplicationManager.getApplication().isDisposed()) {
      return true;
    }

    final EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (settings == null || EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE.equals(settings.getStripTrailingSpaces())) {
      return true;
    }

    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (!project.isInitialized() || project.isDisposed()) {
        continue;
      }
      LookupEx lookup = LookupManager.getInstance(project).getActiveLookup();
      if (lookup != null && isInTrailingSpace(lookup, document)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isInTrailingSpace(Lookup lookup, Document document) {
    Editor editor = InjectedLanguageUtil.getTopLevelEditor(lookup.getEditor());
    if (editor.getDocument() != document) {
      return false;
    }

    int caret = editor.getCaretModel().getOffset();
    if (caret <= 0 || caret >= document.getTextLength()) {
      return false;
    }

    CharSequence seq = document.getCharsSequence();
    if (seq.charAt(caret) == '\n' && Character.isWhitespace(seq.charAt(caret - 1))) {
      return true;
    }

    return false;
  }

  @Override
  public boolean mayReloadFileContent(VirtualFile file, @NotNull Document document) {
    return true;
  }
}
