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

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.Producer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.tree.TreeModelAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 5/13/13 4:18 PM
 */
public class ExternalSystemTasksTree extends Tree implements Producer<ExternalTaskExecutionInfo> {

  private static final int COLLAPSE_STATE_PROCESSING_DELAY_MILLIS = 200;

  @NotNull private static final Comparator<TreePath> PATH_COMPARATOR = new Comparator<TreePath>() {
    @Override
    public int compare(TreePath o1, TreePath o2) {
      return o2.getPathCount() - o1.getPathCount();
    }
  };

  @NotNull private final Alarm myCollapseStateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  /** Holds list of paths which 'expand/collapse' state should be restored. */
  @NotNull private final Set<TreePath> myPathsToProcessCollapseState = ContainerUtilRt.newHashSet();

  @NotNull private final Map<String/*tree path*/, Boolean/*expanded*/> myExpandedStateHolder;

  private boolean mySuppressCollapseTracking;

  public ExternalSystemTasksTree(@NotNull ExternalSystemTasksTreeModel model,
                                 @NotNull Map<String/*tree path*/, Boolean/*expanded*/> expandedStateHolder,
                                 @NotNull final Project project,
                                 @NotNull final ProjectSystemId externalSystemId)
  {
    super(model);
    myExpandedStateHolder = expandedStateHolder;
    setRootVisible(false);

    addTreeWillExpandListener(new TreeWillExpandListener() {
      @Override
      public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
        if (!mySuppressCollapseTracking) {
          myExpandedStateHolder.put(getPath(event.getPath()), true);
        }
      }

      @Override
      public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
        if (!mySuppressCollapseTracking) {
          myExpandedStateHolder.put(getPath(event.getPath()), false);
        }
      }
    });

    model.addTreeModelListener(new TreeModelAdapter() {
      @Override
      public void treeStructureChanged(TreeModelEvent e) {
        scheduleCollapseStateAppliance(e.getTreePath());
      }

      @Override
      public void treeNodesInserted(TreeModelEvent e) {
        scheduleCollapseStateAppliance(e.getTreePath());
      }
    });
    new TreeSpeedSearch(this);

    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "Enter");
    getActionMap().put("Enter", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ExternalTaskExecutionInfo task = produce();
        if (task == null) {
          return;
        }
        ExternalSystemUtil.runTask(task.getSettings(), task.getExecutorId(), project, externalSystemId);
      }
    });
  }

  /**
   * Schedules 'collapse/expand' state restoring for the given path. We can't do that immediately from the tree model listener
   * as there is a possible case that other listeners have not been notified about the model state change, hence, attempt to define
   * 'collapse/expand' state may bring us to the inconsistent state.
   *
   * @param path  target path
   */
  private void scheduleCollapseStateAppliance(@NotNull TreePath path) {
    myPathsToProcessCollapseState.add(path);
    myCollapseStateAlarm.cancelAllRequests();
    myCollapseStateAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        // We assume that the paths collection is modified only from the EDT, so, ConcurrentModificationException doesn't have
        // a chance.
        // Another thing is that we sort the paths in order to process the longest first. That is related to the JTree specifics
        // that it automatically expands parent paths on child path expansion.
        List<TreePath> paths = ContainerUtilRt.newArrayList(myPathsToProcessCollapseState);
        myPathsToProcessCollapseState.clear();
        Collections.sort(paths, PATH_COMPARATOR);
        for (TreePath treePath : paths) {
          applyCollapseState(treePath);
        }
        final TreePath rootPath = new TreePath(getModel().getRoot());
        if (isCollapsed(rootPath)) {
          expandPath(rootPath);
        }
      }
    }, COLLAPSE_STATE_PROCESSING_DELAY_MILLIS);
  }

  /**
   * Applies stored 'collapse/expand' state to the node located at the given path.
   *
   * @param path  target path
   */
  private void applyCollapseState(@NotNull TreePath path) {
    final String key = getPath(path);
    final Boolean expanded = myExpandedStateHolder.get(key);
    if (expanded == null) {
      return;
    }
    boolean s = mySuppressCollapseTracking;
    mySuppressCollapseTracking = true;
    try {
      if (expanded) {
        expandPath(path);
      }
      else {
        collapsePath(path);
      }
    }
    finally {
      mySuppressCollapseTracking = s;
    }
  }

  @NotNull
  private static String getPath(@NotNull TreePath path) {
    StringBuilder buffer = new StringBuilder();
    for (TreePath current = path; current != null; current = current.getParentPath()) {
      buffer.append(current.getLastPathComponent().toString()).append('/');
    }
    buffer.setLength(buffer.length() - 1);
    return buffer.toString();
  }

  @Nullable
  @Override
  public ExternalTaskExecutionInfo produce() {
    TreePath[] selectionPaths = getSelectionPaths();
    if (selectionPaths == null || selectionPaths.length == 0) {
      return null;
    }

    Map<String, ExternalTaskExecutionInfo> map = ContainerUtil.newHashMap();
    for (TreePath selectionPath : selectionPaths) {
      Object component = selectionPath.getLastPathComponent();
      if (!(component instanceof ExternalSystemNode)) {
        continue;
      }

      Object element = ((ExternalSystemNode)component).getDescriptor().getElement();
      if (element instanceof ExternalTaskExecutionInfo) {
        ExternalTaskExecutionInfo taskExecutionInfo = (ExternalTaskExecutionInfo)element;
        ExternalSystemTaskExecutionSettings executionSettings = taskExecutionInfo.getSettings();
        String key = executionSettings.getExternalSystemIdString() + executionSettings.getExternalProjectPath() + executionSettings.getVmOptions();
        ExternalTaskExecutionInfo executionInfo = map.get(key);
        if(executionInfo == null) {
          ExternalSystemTaskExecutionSettings taskExecutionSettings = new ExternalSystemTaskExecutionSettings();
          taskExecutionSettings.setExternalProjectPath(executionSettings.getExternalProjectPath());
          taskExecutionSettings.setExternalSystemIdString(executionSettings.getExternalSystemIdString());
          taskExecutionSettings.setVmOptions(executionSettings.getVmOptions());
          executionInfo = new ExternalTaskExecutionInfo(taskExecutionSettings, taskExecutionInfo.getExecutorId());
          map.put(key, executionInfo);
        }
        executionInfo.getSettings().getTaskNames().addAll(executionSettings.getTaskNames());
        executionInfo.getSettings().getTaskDescriptions().addAll(executionSettings.getTaskDescriptions());
      }
    }

    // Disable tasks execution if it comes from different projects
    if(map.values().size() != 1) return null;
    return map.values().iterator().next();
  }
}
