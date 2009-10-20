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
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.SourceItemsTree;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.ui.PackagingSourceItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public class PackAndPutIntoDefaultLocationAction extends PutIntoDefaultLocationActionBase {
  public PackAndPutIntoDefaultLocationAction(SourceItemsTree sourceItemsTree, ArtifactEditorEx artifactEditor) {
    super(sourceItemsTree, artifactEditor);
  }

  @Override
  public void update(AnActionEvent e) {
    final String jarName = suggestJarName();
    final String pathForJars = myArtifactEditor.getArtifact().getArtifactType().getDefaultPathFor(PackagingElementOutputKind.JAR_FILES);
    final Presentation presentation = e.getPresentation();
    if (jarName != null && pathForJars != null) {
      presentation.setText("Pack Into " + DeploymentUtil.appendToPath(pathForJars, jarName + ".jar"));
      presentation.setVisible(true);
    }
    else {
      presentation.setVisible(false);
    }
  }

  @Nullable
  private String suggestJarName() {
    final List<PackagingSourceItem> items = mySourceItemsTree.getSelectedItems();
    for (PackagingSourceItem item : items) {
      if (item.isProvideElements() && item.getKindOfProducedElements().containsDirectoriesWithClasses()) {
        return item.createPresentation(myArtifactEditor.getContext()).getPresentableName();
      }
    }
    return null;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final String pathForJars = myArtifactEditor.getArtifact().getArtifactType().getDefaultPathFor(PackagingElementOutputKind.JAR_FILES);
    final String jarName = suggestJarName();
    if (pathForJars != null) {
      myArtifactEditor.getLayoutTreeComponent().packInto(mySourceItemsTree.getSelectedItems(), 
                                                         DeploymentUtil.appendToPath(pathForJars, jarName + ".jar"));
    }
  }
}
