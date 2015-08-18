/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.ui;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.view.ExternalProjectsStructure;
import com.intellij.openapi.externalSystem.view.ExternalProjectsView;
import com.intellij.openapi.externalSystem.view.ExternalProjectsViewAdapter;
import com.intellij.openapi.externalSystem.view.ExternalSystemNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleNodeVisitor;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeSelectionModel;
import java.awt.event.InputEvent;
import java.util.Collection;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 4/15/2015
 */
public class SelectExternalSystemNodeDialog extends DialogWrapper {

  @NotNull
  private final SimpleTree myTree;
  @Nullable
  private final NodeSelector mySelector;
  @Nullable
  protected Boolean groupTasks;
  @Nullable
  protected Boolean useTasksNode;

  public SelectExternalSystemNodeDialog(@NotNull ProjectSystemId systemId,
                                        @NotNull Project project,
                                        @NotNull String title,
                                        Class<? extends ExternalSystemNode> nodeClass,
                                        @Nullable NodeSelector selector) {
    //noinspection unchecked
    this(systemId, project, title, new Class[]{nodeClass}, selector);
  }

  public SelectExternalSystemNodeDialog(@NotNull ProjectSystemId systemId,
                                        @NotNull Project project,
                                        @NotNull String title,
                                        final Class<? extends ExternalSystemNode>[] nodeClasses,
                                        @Nullable NodeSelector selector) {
    super(project, false);
    mySelector = selector;
    setTitle(title);

    myTree = new SimpleTree();
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    final ExternalProjectsView projectsView = ExternalProjectsManager.getInstance(project).getExternalProjectsView(systemId);
    if(projectsView != null) {
      final ExternalProjectsStructure treeStructure = new ExternalProjectsStructure(project, myTree) {
        @SuppressWarnings("unchecked")
        @Override
        protected Class<? extends ExternalSystemNode>[] getVisibleNodesClasses() {
          return nodeClasses;
        }

        @Override
        public Object getRootElement() {
          Object rootElement = super.getRootElement();
          return customizeProjectsTreeRoot(rootElement);
        }
      };
      Disposer.register(myDisposable, treeStructure);
      treeStructure.init(new ExternalProjectsViewAdapter(projectsView) {
        @Nullable
        @Override
        public ExternalProjectsStructure getStructure() {
          return treeStructure;
        }

        @Override
        public void updateUpTo(ExternalSystemNode node) {
          treeStructure.updateUpTo(node);
        }

        @Override
        public boolean getGroupTasks() {
          return groupTasks != null ? groupTasks : super.getGroupTasks();
        }

        @Override
        public boolean useTasksNode() {
          return useTasksNode != null ? useTasksNode : super.useTasksNode();
        }

        @Override
        public void handleDoubleClickOrEnter(@NotNull ExternalSystemNode node, @Nullable String actionId, InputEvent inputEvent) {
          SelectExternalSystemNodeDialog.this.handleDoubleClickOrEnter(node, actionId, inputEvent);
        }
      });

      final Collection<ExternalProjectInfo> projectsData =
        ProjectDataManager.getInstance().getExternalProjectsData(project, systemId);

      final List<DataNode<ProjectData>> dataNodes =
        ContainerUtil.mapNotNull(projectsData, new Function<ExternalProjectInfo, DataNode<ProjectData>>() {
          @Override
          public DataNode<ProjectData> fun(ExternalProjectInfo info) {
            return info.getExternalProjectStructure();
          }
        });
      treeStructure.updateProjects(dataNodes);
      TreeUtil.expandAll(myTree);

      if (mySelector != null) {
        final SimpleNode[] selection = new SimpleNode[]{null};
        treeStructure.accept(new SimpleNodeVisitor() {
          public boolean accept(SimpleNode each) {
            if (!mySelector.shouldSelect(each)) return false;
            selection[0] = each;
            return true;
          }
        });
        if (selection[0] != null) {
          treeStructure.select(selection[0]);
        }
      }
    }

    init();
  }

  protected Object customizeProjectsTreeRoot(Object rootElement) {
    return rootElement;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  protected void handleDoubleClickOrEnter(@NotNull ExternalSystemNode node, @Nullable String actionId, InputEvent inputEvent) {
  }

  protected SimpleNode getSelectedNode() {
    return myTree.getNodeFor(myTree.getSelectionPath());
  }

  @Nullable
  protected JComponent createCenterPanel() {
    final JScrollPane pane = ScrollPaneFactory.createScrollPane(myTree);
    pane.setPreferredSize(JBUI.size(320, 400));
    return pane;
  }

  protected interface NodeSelector {
    boolean shouldSelect(SimpleNode node);
  }
}
