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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;

/**
* @author nik
*/
public class ArtifactEditorNavigateAction extends DumbAwareAction {
  private LayoutTreeComponent myLayoutTreeComponent;

  public ArtifactEditorNavigateAction(LayoutTreeComponent layoutTreeComponent) {
    super(ProjectBundle.message("action.name.facet.navigate"));
    registerCustomShortcutSet(CommonShortcuts.getEditSource(), layoutTreeComponent.getLayoutTree());
    myLayoutTreeComponent = layoutTreeComponent;
  }

  public void update(final AnActionEvent e) {
    PackagingElementNode<?> node = myLayoutTreeComponent.getSelection().getNodeIfSingle();
    e.getPresentation().setEnabled(node != null && node.getElementPresentation().canNavigateToSource());
  }

  public void actionPerformed(final AnActionEvent e) {
    PackagingElementNode<?> node = myLayoutTreeComponent.getSelection().getNodeIfSingle();
    if (node != null) {
      node.getElementPresentation().navigateToSource();
    }
  }
}
