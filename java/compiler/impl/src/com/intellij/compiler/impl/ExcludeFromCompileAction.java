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
package com.intellij.compiler.impl;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.ide.errorTreeView.ErrorTreeElement;
import com.intellij.ide.errorTreeView.ErrorTreeNodeDescriptor;
import com.intellij.ide.errorTreeView.GroupingElement;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
* @author Eugene Zhuravlev
*/
class ExcludeFromCompileAction extends AnAction {
  private final Project myProject;
  private final NewErrorTreeViewPanel myErrorTreeView;

  public ExcludeFromCompileAction(Project project, NewErrorTreeViewPanel errorTreeView) {
    super(CompilerBundle.message("actions.exclude.from.compile.text"));
    myProject = project;
    myErrorTreeView = errorTreeView;
  }

  public void actionPerformed(AnActionEvent e) {
    VirtualFile file = getSelectedFile();

    if (file != null && file.isValid()) {
      ExcludeEntryDescription description = new ExcludeEntryDescription(file, false, true, myProject);
      CompilerConfiguration.getInstance(myProject).getExcludedEntriesConfiguration().addExcludeEntryDescription(description);
      FileStatusManager.getInstance(myProject).fileStatusesChanged();
    }
  }

  @Nullable
  private VirtualFile getSelectedFile() {
    final ErrorTreeNodeDescriptor descriptor = myErrorTreeView.getSelectedNodeDescriptor();
    ErrorTreeElement element = descriptor != null? descriptor.getElement() : null;
    if (element != null && !(element instanceof GroupingElement)) {
      NodeDescriptor parent = descriptor.getParentDescriptor();
      if (parent instanceof ErrorTreeNodeDescriptor) {
        element = ((ErrorTreeNodeDescriptor)parent).getElement();
      }
    }
    return element instanceof GroupingElement? ((GroupingElement)element).getFile() : null;
  }

  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final boolean isApplicable = getSelectedFile() != null;
    presentation.setEnabled(isApplicable);
    presentation.setVisible(isApplicable);
  }
}
