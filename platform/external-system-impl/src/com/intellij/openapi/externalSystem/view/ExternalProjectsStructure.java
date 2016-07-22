/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.treeStructure.*;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 * @since 9/22/2014
 */
public class ExternalProjectsStructure extends SimpleTreeStructure implements Disposable  {
  private final Project myProject;
  private ExternalProjectsView myExternalProjectsView;
  private final SimpleTreeBuilder myTreeBuilder;
  private RootNode myRoot;

  private final Map<String, ExternalSystemNode> myNodeMapping = new THashMap<>();

  public ExternalProjectsStructure(Project project, SimpleTree tree) {
    myProject = project;

    configureTree(tree);

    myTreeBuilder = new SimpleTreeBuilder(tree, (DefaultTreeModel)tree.getModel(), this, null);
    Disposer.register(myProject, myTreeBuilder);
  }

  public void init(ExternalProjectsView externalProjectsView) {
    myExternalProjectsView = externalProjectsView;
    myRoot = new RootNode();
    myTreeBuilder.initRoot();
    myTreeBuilder.expand(myRoot, null);
  }

  public Project getProject() {
    return myProject;
  }

  public void updateFrom(SimpleNode node) {
    myTreeBuilder.addSubtreeToUpdateByElement(node);
  }

  public void updateUpTo(SimpleNode node) {
    SimpleNode each = node;
    while (each != null) {
      updateFrom(each);
      each = each.getParent();
    }
  }

  @Override
  public Object getRootElement() {
    return myRoot;
  }

  private static void configureTree(final SimpleTree tree) {
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
  }

  public void accept(@NotNull SimpleNodeVisitor visitor) {
    if (myTreeBuilder.getTree() instanceof SimpleTree) {
      ((SimpleTree)myTreeBuilder.getTree()).accept(myTreeBuilder, visitor);
    }
  }

  public void select(SimpleNode node) {
    myTreeBuilder.select(node, null);
  }

  protected Class<? extends ExternalSystemNode>[] getVisibleNodesClasses() {
    return null;
  }

  public void updateProjects(Collection<DataNode<ProjectData>> toImport) {
    List<String> orphanProjects = ContainerUtil.mapNotNull(
      myNodeMapping.entrySet(), entry -> entry.getValue() instanceof ProjectNode ? entry.getKey() : null);
    for (DataNode<ProjectData> each : toImport) {
      final ProjectData projectData = each.getData();
      final String projectPath = projectData.getLinkedExternalProjectPath();
      orphanProjects.remove(projectPath);

      ExternalSystemNode projectNode = findNodeFor(projectPath);

      if (projectNode instanceof ProjectNode) {
        doMergeChildrenChanges(projectNode, each, new ProjectNode(myExternalProjectsView, each));
      }
      else {
        ExternalSystemNode node = myNodeMapping.remove(projectPath);
        if (node != null) {
          SimpleNode parent = node.getParent();
          if (parent instanceof ExternalSystemNode) {
            ((ExternalSystemNode)parent).remove(projectNode);
          }
        }

        projectNode = new ProjectNode(myExternalProjectsView, each);
        myNodeMapping.put(projectPath, projectNode);
      }
      if (toImport.size() == 1) {
        myTreeBuilder.expand(projectNode, null);
      }
      doUpdateProject((ProjectNode)projectNode);
    }

    //remove orphan projects from view
    for (String orphanProjectPath : orphanProjects) {
      ExternalSystemNode projectNode = myNodeMapping.remove(orphanProjectPath);
      if (projectNode instanceof ProjectNode) {
        SimpleNode parent = projectNode.getParent();
        if (parent instanceof ExternalSystemNode) {
          ((ExternalSystemNode)parent).remove(projectNode);
          updateUpTo(projectNode);
        }
      }
    }
  }

  private void doMergeChildrenChanges(ExternalSystemNode currentNode, DataNode<?> newDataNode, ExternalSystemNode newNode) {
    final ExternalSystemNode[] cached = currentNode.getCached();
    if (cached != null) {

      final List<Object> duplicates = ContainerUtil.newArrayList();
      final Map<Object, ExternalSystemNode> oldDataMap = ContainerUtil.newLinkedHashMap();
      for (ExternalSystemNode node : cached) {
        Object key = node.getData() != null ? node.getData() : node.getName();
        final Object systemNode = oldDataMap.put(key, node);
        if(systemNode != null) {
          duplicates.add(key);
        }
      }

      Map<Object, ExternalSystemNode> newDataMap = ContainerUtil.newLinkedHashMap();
      Map<Object, ExternalSystemNode> unchangedNewDataMap = ContainerUtil.newLinkedHashMap();
      for (ExternalSystemNode node : newNode.getChildren()) {
        Object key = node.getData() != null ? node.getData() : node.getName();
        if (oldDataMap.remove(key) == null) {
          newDataMap.put(key, node);
        }
        else {
          unchangedNewDataMap.put(key, node);
        }
      }

      for (Object duplicate : duplicates) {
        newDataMap.remove(duplicate);
      }

      currentNode.removeAll(oldDataMap.values());

      for (ExternalSystemNode node : currentNode.getChildren()) {
        Object key = node.getData() != null ? node.getData() : node.getName();
        final ExternalSystemNode unchangedNewNode = unchangedNewDataMap.get(key);
        if (unchangedNewNode != null) {
          doMergeChildrenChanges(node, unchangedNewNode.myDataNode, unchangedNewNode);
        }
      }

      updateFrom(currentNode);
      currentNode.addAll(newDataMap.values());
    }
    //noinspection unchecked
    currentNode.setDataNode(newDataNode);
  }

  private void doUpdateProject(ProjectNode node) {
    ExternalSystemNode newParentNode = myRoot;
    if (!node.isVisible()) {
      newParentNode.remove(node);
    }
    else {
      node.updateProject();
      reconnectNode(node, newParentNode);
    }
  }

  private static void reconnectNode(ProjectNode node, ExternalSystemNode newParentNode) {
    ExternalSystemNode oldParentNode = node.getGroup();
    if (oldParentNode == null || !oldParentNode.equals(newParentNode)) {
      if (oldParentNode != null) {
        oldParentNode.remove(node);
      }
      newParentNode.add(node);
    }
  }

  @SuppressWarnings("SuspiciousMethodCalls")
  private ExternalSystemNode findNodeFor(String projectPath) {
    return myNodeMapping.get(projectPath);
  }

  public <T extends ExternalSystemNode> void updateNodes(@NotNull Class<T> nodeClass) {
    for (T node : getNodes(nodeClass)) {
      updateFrom(node);
    }
  }

  public <T extends ExternalSystemNode> void visitNodes(@NotNull Class<T> nodeClass, @NotNull Consumer<T> consumer) {
    for (T node : getNodes(nodeClass)) {
      consumer.consume(node);
    }
  }

  @Override
  public void dispose() {
    this.myExternalProjectsView = null;
    this.myNodeMapping.clear();
    this.myRoot = null;
  }

  public class RootNode<T> extends ExternalSystemNode<T> {
    public RootNode() {
      super(myExternalProjectsView, null, null);
    }

    @Override
    public boolean isVisible() {
      return true;
    }
  }

  public enum ErrorLevel {
    NONE, ERROR
  }

  enum DisplayKind {
    ALWAYS, NEVER, NORMAL
  }

  @NotNull
  public <T extends ExternalSystemNode> List<T> getNodes(@NotNull Class<T> nodeClass) {
    return doGetNodes(nodeClass, myRoot.getChildren(), new SmartList<>());
  }

  @NotNull
  private static <T extends ExternalSystemNode> List<T> doGetNodes(@NotNull Class<T> nodeClass,
                                                                   SimpleNode[] nodes,
                                                                   @NotNull List<T> result) {
    if (nodes == null) return result;

    for (SimpleNode node : nodes) {
      if (nodeClass.isInstance(node)) {
        //noinspection unchecked
        result.add((T)node);
      }
      doGetNodes(nodeClass, node.getChildren(), result);
    }
    return result;
  }

  @NotNull
  public <T extends ExternalSystemNode> List<T> getSelectedNodes(SimpleTree tree, Class<T> nodeClass) {
    final List<T> filtered = new ArrayList<>();
    for (SimpleNode node : getSelectedNodes(tree)) {
      if ((nodeClass != null) && (!nodeClass.isInstance(node))) {
        filtered.clear();
        break;
      }
      //noinspection unchecked
      filtered.add((T)node);
    }
    return filtered;
  }

  private static List<SimpleNode> getSelectedNodes(SimpleTree tree) {
    List<SimpleNode> nodes = new ArrayList<>();
    TreePath[] treePaths = tree.getSelectionPaths();
    if (treePaths != null) {
      for (TreePath treePath : treePaths) {
        nodes.add(tree.getNodeFor(treePath));
      }
    }
    return nodes;
  }
}
