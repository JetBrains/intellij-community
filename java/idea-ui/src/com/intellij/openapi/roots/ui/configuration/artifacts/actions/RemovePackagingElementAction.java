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
package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeSelection;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

public class RemovePackagingElementAction extends LayoutTreeActionBase {

  public RemovePackagingElementAction(@NotNull ArtifactEditorEx artifactEditor) {
    super(JavaUiBundle.message("action.name.remove.packaging.element"),
          JavaUiBundle.message("action.description.remove.packaging.elements"),
          PlatformIcons.DELETE_ICON,
          artifactEditor);
  }

  @Override
  protected boolean isEnabled() {
    final LayoutTreeSelection selection = myArtifactEditor.getLayoutTreeComponent().getSelection();
    if (selection.getElements().isEmpty() || myArtifactEditor.getLayoutTreeComponent().isEditing()) {
      return false;
    }
    for (PackagingElementNode<?> node : selection.getNodes()) {
      if (node.getParentNode() == null) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myArtifactEditor.removeSelectedElements();
  }
}
