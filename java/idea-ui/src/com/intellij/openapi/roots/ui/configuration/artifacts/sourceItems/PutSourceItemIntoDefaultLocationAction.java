/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.ui.PackagingSourceItem;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * @author nik
 */
public class PutSourceItemIntoDefaultLocationAction extends AnAction {
  private final SourceItemsTree mySourceItemsTree;
  private final ArtifactEditorEx myArtifactEditor;

  public PutSourceItemIntoDefaultLocationAction(SourceItemsTree sourceItemsTree, ArtifactEditorEx artifactEditor) {
    mySourceItemsTree = sourceItemsTree;
    myArtifactEditor = artifactEditor;
  }

  @Override
  public void update(AnActionEvent e) {
    final ArtifactType type = myArtifactEditor.getArtifact().getArtifactType();
    final List<PackagingSourceItem> items = mySourceItemsTree.getSelectedItems();
    boolean enabled = false;
    final Presentation presentation = e.getPresentation();
    if (!items.isEmpty()) {
      enabled = true;
      Set<String> paths = new HashSet<String>();
      for (PackagingSourceItem item : items) {
        final String path = type.getDefaultPathFor(item);
        if (path == null) {
          enabled = false;
          break;
        }
        paths.add(StringUtil.trimStart(StringUtil.trimEnd(path, "/"), "/"));
      }
      if (paths.size() == 1) {
        presentation.setText("Put Into /" + paths.iterator().next());
      }
      else {
        presentation.setText("Put into default locations");
      }
    }
    presentation.setVisible(enabled);
    presentation.setEnabled(enabled);
  }

  public void actionPerformed(AnActionEvent e) {
    myArtifactEditor.getLayoutTreeComponent().putIntoDefaultLocations(mySourceItemsTree.getSelectedItems());
  }
}
