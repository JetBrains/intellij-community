/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ExcludeFolder;
import com.intellij.openapi.roots.ui.configuration.ContentEntryEditor;
import com.intellij.openapi.roots.ui.configuration.ContentEntryTreeEditor;
import com.intellij.openapi.roots.ui.configuration.IconSet;

import javax.swing.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 * @since Oct 14, 2003
 */
public class ToggleExcludedStateAction extends ContentEntryEditingAction {
  private final ContentEntryTreeEditor myEntryTreeEditor;

  public ToggleExcludedStateAction(JTree tree, ContentEntryTreeEditor entryEditor) {
    super(tree);
    myEntryTreeEditor = entryEditor;
    final Presentation templatePresentation = getTemplatePresentation();
    templatePresentation.setText(ProjectBundle.message("module.toggle.excluded.action"));
    templatePresentation.setDescription(ProjectBundle.message("module.toggle.excluded.action.description"));
    templatePresentation.setIcon(IconSet.EXCLUDE_FOLDER);
  }

  @Override
  public boolean isSelected(final AnActionEvent e) {
    final List<String> selectedPaths = getSelectedPaths();
    if (selectedPaths.size() == 0) return false;

    final ContentEntryEditor editor = myEntryTreeEditor.getContentEntryEditor();
    return editor.isExcluded(selectedPaths.get(0)) || editor.isUnderExcludedDirectory(selectedPaths.get(0));
  }

  @Override
  public void setSelected(final AnActionEvent e, final boolean isSelected) {
    final List<String> selectedPaths = getSelectedPaths();
    assert selectedPaths.size() != 0;

    for (String selectedPath : selectedPaths) {
      final ExcludeFolder excludeFolder = myEntryTreeEditor.getContentEntryEditor().getExcludeFolder(selectedPath);
      if (isSelected) {
        if (excludeFolder == null) { // not excluded yet
          myEntryTreeEditor.getContentEntryEditor().addExcludeFolder(selectedPath);
        }
      }
      else {
        if (excludeFolder != null) {
          myEntryTreeEditor.getContentEntryEditor().removeExcludeFolder(excludeFolder);
        }
      }
    }
  }

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    final Presentation presentation = e.getPresentation();
    presentation.setText(ProjectBundle.message("module.toggle.excluded.action"));
  }
}
