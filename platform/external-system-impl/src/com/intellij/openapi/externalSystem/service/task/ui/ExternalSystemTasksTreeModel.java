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
package com.intellij.openapi.externalSystem.service.task.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.serialization.ExternalProjectPojo;
import com.intellij.openapi.externalSystem.model.serialization.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.service.ui.DefaultExternalSystemUiAware;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.util.containers.ContainerUtilRt;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 5/12/13 10:28 PM
 */
public class ExternalSystemTasksTreeModel extends DefaultTreeModel {

  private static final Logger LOG = Logger.getInstance("#" + ExternalSystemTasksTreeModel.class.getName());

  @NotNull private static final Comparator<TreeNode> NODE_COMPARATOR = new Comparator<TreeNode>() {
    @Override
    public int compare(TreeNode t1, TreeNode t2) {
      Object e1 = ((ExternalSystemNode<?>)t1).getDescriptor().getElement();
      Object e2 = ((ExternalSystemNode<?>)t2).getDescriptor().getElement();
      if (e1 instanceof ExternalProjectPojo) {
        if (e2 instanceof ExternalTaskPojo) {
          return 1;
        }
        else {
          return ((ExternalProjectPojo)e1).getName().compareTo(((ExternalProjectPojo)e2).getName());
        }
      }
      else {
        if (e2 instanceof ExternalProjectPojo) {
          return -1;
        }
        else {
          return ((ExternalTaskPojo)e1).getName().compareTo(((ExternalTaskPojo)e2).getName());
        }
      }
    }
  };

  @NotNull private final TreeNode[] myNodeHolder  = new TreeNode[1];
  @NotNull private final int[]      myIndexHolder = new int[1];
  @NotNull private final ExternalSystemUiAware myUiAware;

  public ExternalSystemTasksTreeModel(@NotNull ProjectSystemId externalSystemId) {
    super(new ExternalSystemNode<String>(new ExternalSystemNodeDescriptor<String>("", "", null)));
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    if (manager instanceof ExternalSystemUiAware) {
      myUiAware = (ExternalSystemUiAware)manager;
    }
    else {
      myUiAware = DefaultExternalSystemUiAware.INSTANCE;
    }
  }

  /**
   * Ensures that current model has a top-level node which corresponds to the given external project info holder
   *
   * @param project  target external project info holder
   */
  @SuppressWarnings("unchecked")
  @NotNull
  public ExternalSystemNode<ExternalProjectPojo> ensureProjectNodeExists(@NotNull ExternalProjectPojo project) {
    ExternalSystemNode<?> root = getRoot();

    // Remove outdated projects.
    for (int i = root.getChildCount() - 1; i >= 0; i--) {
      ExternalSystemNode<?> child = root.getChildAt(i);
      Object element = child.getDescriptor().getElement();
      if (element instanceof ExternalProjectPojo
          && ((ExternalProjectPojo)element).getPath().equals(project.getPath()))
      {
        return (ExternalSystemNode<ExternalProjectPojo>)child;
      }
    }
    ExternalProjectPojo element = new ExternalProjectPojo(project.getName(), project.getPath());
    ExternalSystemNodeDescriptor<ExternalProjectPojo> descriptor = descriptor(element, myUiAware.getProjectIcon());
    myIndexHolder[0] = root.getChildCount();
    ExternalSystemNode<ExternalProjectPojo> result = new ExternalSystemNode<ExternalProjectPojo>(descriptor);
    root.add(result);
    nodesWereInserted(root, myIndexHolder);
    return result;
  }

  public void ensureSubProjectsStructure(@NotNull ExternalProjectPojo topLevelProject,
                                         @NotNull Collection<ExternalProjectPojo> subProjects)
  {
    ExternalSystemNode<ExternalProjectPojo> topLevelProjectNode = ensureProjectNodeExists(topLevelProject);
    Map<String/*config path*/, ExternalProjectPojo> toAdd = ContainerUtilRt.newHashMap();
    for (ExternalProjectPojo subProject : subProjects) {
      toAdd.put(subProject.getPath(), subProject);
    }
    toAdd.remove(topLevelProject.getPath());

    final TObjectIntHashMap<Object> taskWeights = new TObjectIntHashMap<Object>();
    for (int i = 0; i < topLevelProjectNode.getChildCount(); i++) {
      ExternalSystemNode<?> child = topLevelProjectNode.getChildAt(i);
      Object childElement = child.getDescriptor().getElement();
      if (childElement instanceof ExternalTaskPojo) {
        taskWeights.put(childElement, subProjects.size() + i);
        continue;
      }
      
      if (toAdd.remove(((ExternalProjectPojo)childElement).getPath()) == null) {
        topLevelProjectNode.remove(child);
        myIndexHolder[0] = i;
        myNodeHolder[0] = child;
        nodesWereRemoved(topLevelProjectNode, myIndexHolder, myNodeHolder);
        //noinspection AssignmentToForLoopParameter
        i--;
      }
    }
    if (!toAdd.isEmpty()) {
      for (Map.Entry<String, ExternalProjectPojo> entry : toAdd.entrySet()) {
        ExternalProjectPojo
          element = new ExternalProjectPojo(entry.getValue().getName(), entry.getValue().getPath());
        topLevelProjectNode.add(new ExternalSystemNode<ExternalProjectPojo>(descriptor(element, myUiAware.getProjectIcon())));
        myIndexHolder[0] = topLevelProjectNode.getChildCount() - 1;
        nodesWereInserted(topLevelProjectNode, myIndexHolder);
      }
    }

    ExternalSystemUiUtil.sort(topLevelProjectNode, this, NODE_COMPARATOR);
  }

  public void ensureTasks(@NotNull String externalProjectConfigPath, @NotNull Collection<ExternalTaskPojo> tasks) {
    ExternalSystemNode<ExternalProjectPojo> moduleNode = findProjectNode(externalProjectConfigPath);
    if (moduleNode == null) {
      LOG.warn(String.format(
        "Can't proceed tasks for module which external config path is '%s'. Reason: no such module node is found. Tasks: %s",
        externalProjectConfigPath, tasks
      ));
      return;
    }
    Set<ExternalTaskPojo> toAdd = ContainerUtilRt.newHashSet(tasks);
    for (int i = 0; i < moduleNode.getChildCount(); i++) {
      ExternalSystemNode<?> childNode = moduleNode.getChildAt(i);
      Object element = childNode.getDescriptor().getElement();
      if (element instanceof ExternalTaskPojo) {
        if (!toAdd.remove(element)) {
          moduleNode.remove(childNode);
          myIndexHolder[0] = i;
          myNodeHolder[0] = childNode;
          nodesWereRemoved(moduleNode, myIndexHolder, myNodeHolder);
        }
      }
    }
    
    if (!toAdd.isEmpty()) {
      for (ExternalTaskPojo pojo : toAdd) {
        moduleNode.add(new ExternalSystemNode<ExternalTaskPojo>(descriptor(pojo, myUiAware.getTaskIcon())));
        myIndexHolder[0] = moduleNode.getChildCount() - 1;
        nodesWereInserted(moduleNode, myIndexHolder);
      }
    }
    ExternalSystemUiUtil.sort(moduleNode, this, NODE_COMPARATOR);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  private ExternalSystemNode<ExternalProjectPojo> findProjectNode(@NotNull String configPath) {
    for (int i = getRoot().getChildCount() - 1; i >= 0; i--) {
      ExternalSystemNode<?> child = getRoot().getChildAt(i);
      Object childElement = child.getDescriptor().getElement();
      if (childElement instanceof ExternalProjectPojo && ((ExternalProjectPojo)childElement).getPath().equals(configPath)) {
        return (ExternalSystemNode<ExternalProjectPojo>)child;
      }
      for (int j = child.getChildCount() - 1; j >= 0; j--) {
        ExternalSystemNode<?> grandChild = child.getChildAt(j);
        Object grandChildElement = grandChild.getDescriptor().getElement();
        if (grandChildElement instanceof ExternalProjectPojo
            && ((ExternalProjectPojo)grandChildElement).getPath().equals(configPath))
        {
          return (ExternalSystemNode<ExternalProjectPojo>)grandChild;
        }
      }
    }
    return null;
  }

  @NotNull
  private static <T> ExternalSystemNodeDescriptor<T> descriptor(@NotNull T element, @Nullable Icon icon) {
    return new ExternalSystemNodeDescriptor<T>(element, element.toString(), icon);
  }
  
  @NotNull
  public ExternalSystemNode<?> getRoot() {
    return (ExternalSystemNode<?>)super.getRoot();
  }
}
