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

package com.intellij.platform;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode;
import com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode;
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class PlatformProjectViewStructureProvider implements TreeStructureProvider, DumbAware {
  private final Project myProject;

  public PlatformProjectViewStructureProvider(Project project) {
    myProject = project;
  }

  public Collection<AbstractTreeNode> modify(final AbstractTreeNode parent, final Collection<AbstractTreeNode> children, final ViewSettings settings) {
    if (parent instanceof ProjectViewProjectNode) {
      int foundModules = 0;
      List<AbstractTreeNode> allChildren = new ArrayList<AbstractTreeNode>();
      for(AbstractTreeNode child: children) {
        if (child instanceof ProjectViewModuleNode) {
          foundModules++;
          allChildren.addAll(child.getChildren());
        }
        else if (child instanceof ExternalLibrariesNode) {
          allChildren.add(child);
        }
      }
      if (foundModules == 1) {
        return allChildren;
      }
    }
    else if (parent instanceof PsiDirectoryNode) {
      final VirtualFile vFile = ((PsiDirectoryNode)parent).getVirtualFile();
      if (vFile != null && vFile.equals(myProject.getBaseDir())) {
        final Collection<AbstractTreeNode> moduleChildren = ((PsiDirectoryNode) parent).getChildren();
        Collection<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
        for (AbstractTreeNode moduleChild : moduleChildren) {
          if (moduleChild instanceof PsiDirectoryNode) {
            final PsiDirectory value = ((PsiDirectoryNode)moduleChild).getValue();
            if (value.getName().equals(".idea")) {
              continue;
            }
          }
          result.add(moduleChild);
        }
        return result;
      }
    }
    return children;
  }

  public Object getData(final Collection<AbstractTreeNode> selected, final String dataName) {
    return null;
  }
}
