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
import com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode;
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.DumbAware;

import java.util.Collection;

/**
 * @author yole
 */
public class PlatformProjectViewStructureProvider implements TreeStructureProvider, DumbAware {
  public Collection<AbstractTreeNode> modify(final AbstractTreeNode parent, final Collection<AbstractTreeNode> children, final ViewSettings settings) {
    if (parent instanceof ProjectViewProjectNode) {
      for(AbstractTreeNode child: children) {
        if (child instanceof ProjectViewModuleNode) {
          return child.getChildren();
        }
      }
    }
    return children;
  }

  public Object getData(final Collection<AbstractTreeNode> selected, final String dataName) {
    return null;
  }
}
