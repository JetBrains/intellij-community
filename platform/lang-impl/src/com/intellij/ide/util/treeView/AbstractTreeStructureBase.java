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

package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbService;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class AbstractTreeStructureBase extends AbstractTreeStructure {
  protected final Project myProject;

  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.treeView.AbstractTreeStructureBase");


  protected AbstractTreeStructureBase(Project project) {
    myProject = project;
  }

  public Object[] getChildElements(Object element) {
    LOG.assertTrue(element instanceof AbstractTreeNode, element != null ? element.getClass().getName() : null);
    AbstractTreeNode<?> treeNode = (AbstractTreeNode)element;
    Collection<? extends AbstractTreeNode> elements = treeNode.getChildren();
    List<TreeStructureProvider> providers = getProvidersDumbAware();
    if (providers != null && !providers.isEmpty()) {
      for (TreeStructureProvider provider : providers) {
        elements = provider.modify(treeNode, (Collection<AbstractTreeNode>)elements, ViewSettings.DEFAULT);
      }
    }
    for (AbstractTreeNode node : elements) {
      node.setParent(treeNode);
    }

    return ArrayUtil.toObjectArray(elements);
  }

  public Object getParentElement(Object element) {
    if (element instanceof AbstractTreeNode){
      return ((AbstractTreeNode)element).getParent();
    }
    return null;
  }

  @NotNull
  public NodeDescriptor createDescriptor(final Object element, final NodeDescriptor parentDescriptor) {
    return (NodeDescriptor)element;
  }

  @Nullable
  public abstract List<TreeStructureProvider> getProviders();

  public Object getDataFromProviders(final List<AbstractTreeNode> selectedNodes, final String dataId) {
    final List<TreeStructureProvider> providers = getProvidersDumbAware();
    if (providers != null) {
      for (TreeStructureProvider treeStructureProvider : providers) {
        final Object fromProvider = treeStructureProvider.getData(selectedNodes, dataId);
        if (fromProvider != null) {
          return fromProvider;
        }
      }
    }
    return null;
  }

  private List<TreeStructureProvider> getProvidersDumbAware() {
    if (myProject == null) {
      return new ArrayList<TreeStructureProvider>();
    }

    final List<TreeStructureProvider> providers = getProviders();
    if (providers == null) {
      return null;
    }

    return DumbService.getInstance(myProject).filterByDumbAwareness(providers);
  }
}
