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

import com.intellij.execution.Location;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectPojo;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemTaskLocation;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Producer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.intellij.openapi.externalSystem.util.ExternalSystemConstants.*;

/**
 * @author Denis Zhdanov
 * @since 5/12/13 10:18 PM
 */
public class ExternalSystemTasksPanel extends SimpleToolWindowPanel implements DataProvider {

  @NotNull private final ExternalSystemRecentTasksList myRecentTasksList;
  @NotNull private final ExternalSystemTasksTreeModel  myAllTasksModel;
  @NotNull private final ExternalSystemTasksTree       myAllTasksTree;
  @NotNull private final ProjectSystemId               myExternalSystemId;
  @NotNull private final NotificationGroup             myNotificationGroup;
  @NotNull private final Project                       myProject;

  @Nullable private Producer<ExternalTaskExecutionInfo> mySelectedTaskProvider;

  public ExternalSystemTasksPanel(@NotNull Project project,
                                  @NotNull ProjectSystemId externalSystemId,
                                  @NotNull NotificationGroup notificationGroup)
  {
    super(true);
    myExternalSystemId = externalSystemId;
    myNotificationGroup = notificationGroup;
    myProject = project;

    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    assert manager != null;
    AbstractExternalSystemLocalSettings settings = manager.getLocalSettingsProvider().fun(project);

    ExternalSystemRecentTaskListModel recentTasksModel = new ExternalSystemRecentTaskListModel(externalSystemId, project);
    recentTasksModel.setTasks(settings.getRecentTasks());
    myRecentTasksList = new ExternalSystemRecentTasksList(recentTasksModel, externalSystemId, project) {
      @Override
      protected void processMouseEvent(MouseEvent e) {
        if (e.getClickCount() > 0) {
          mySelectedTaskProvider = myRecentTasksList;
          myAllTasksTree.getSelectionModel().clearSelection();
        }
        super.processMouseEvent(e);
      }
    };

    myAllTasksModel = new ExternalSystemTasksTreeModel(externalSystemId);
    myAllTasksTree = new ExternalSystemTasksTree(myAllTasksModel, settings.getExpandStates(), project, externalSystemId) {
      @Override
      protected void processMouseEvent(MouseEvent e) {
        if (e.getClickCount() > 0) {
          mySelectedTaskProvider = myAllTasksTree;
          myRecentTasksList.getSelectionModel().clearSelection();
        }
        super.processMouseEvent(e);
      }
    };
    final String actionIdToUseForDoubleClick = DefaultRunExecutor.getRunExecutorInstance().getContextActionId();
    myAllTasksTree.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() >= 2 && !e.isPopupTrigger()) {
          ExternalSystemUiUtil.executeAction(actionIdToUseForDoubleClick, e);
        }
      }
    });
    ExternalSystemUiUtil.apply(settings, myAllTasksModel);
    CustomizationUtil.installPopupHandler(myAllTasksTree, TREE_ACTIONS_GROUP_ID, TREE_CONTEXT_MENU_PLACE);

    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup group = (ActionGroup)actionManager.getAction(TOOL_WINDOW_TOOLBAR_ACTIONS_GROUP_ID);
    ActionToolbar toolbar = actionManager.createActionToolbar(TOOL_WINDOW_PLACE, group, true);
    toolbar.setTargetComponent(this);
    setToolbar(toolbar.getComponent());

    JPanel content = new JPanel(new GridBagLayout());
    content.setOpaque(true);
    content.setBackground(UIUtil.getListBackground());
    JComponent recentTasksWithTitle = wrap(myRecentTasksList, ExternalSystemBundle.message("tasks.recent.title"));
    content.add(recentTasksWithTitle, ExternalSystemUiUtil.getFillLineConstraints(0));
    JBScrollPane scrollPane = new JBScrollPane(myAllTasksTree);
    scrollPane.setBorder(null);
    JComponent allTasksWithTitle = wrap(scrollPane, ExternalSystemBundle.message("tasks.all.title"));
    content.add(allTasksWithTitle, ExternalSystemUiUtil.getFillLineConstraints(0).weighty(1).fillCell());
    setContent(content);
  }

  private static JComponent wrap(@NotNull JComponent content, @NotNull String title) {
    JPanel result = new JPanel(new BorderLayout());
    result.setOpaque(false);
    result.setBorder(IdeBorderFactory.createTitledBorder(title, false));
    result.add(content, BorderLayout.CENTER);
    return result;
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (ExternalSystemDataKeys.RECENT_TASKS_LIST.is(dataId)) {
      return myRecentTasksList;
    }
    else if (ExternalSystemDataKeys.ALL_TASKS_MODEL.is(dataId)) {
      return myAllTasksModel;
    }
    else if (ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID.is(dataId)) {
      return myExternalSystemId;
    }
    else if (ExternalSystemDataKeys.NOTIFICATION_GROUP.is(dataId)) {
      return myNotificationGroup;
    }
    else if (ExternalSystemDataKeys.SELECTED_TASK.is(dataId)) {
      return mySelectedTaskProvider == null ? null : mySelectedTaskProvider.produce();
    }
    else if (ExternalSystemDataKeys.SELECTED_PROJECT.is(dataId)) {
      if (mySelectedTaskProvider != myAllTasksTree) {
        return null;
      }
      else {
        Object component = myAllTasksTree.getLastSelectedPathComponent();
        if (component instanceof ExternalSystemNode) {
          Object element = ((ExternalSystemNode)component).getDescriptor().getElement();
          return element instanceof ExternalProjectPojo ? element : null;
        }
      }
    }
    else if (Location.DATA_KEY.is(dataId)) {
      Location location = buildLocation();
      return location == null ? super.getData(dataId) : location;
    }
    return null;
  }

  @Nullable
  private Location buildLocation() {
    if (mySelectedTaskProvider == null) {
      return null;
    }
    ExternalTaskExecutionInfo task = mySelectedTaskProvider.produce();
    if (task == null) {
      return null;
    }

    String projectPath = task.getSettings().getExternalProjectPath();
    String name = myExternalSystemId.getReadableName() + projectPath + StringUtil.join(task.getSettings().getTaskNames(), " ");
    // We create a dummy text file instead of re-using external system file in order to avoid clashing with other configuration producers.
    // For example gradle files are enhanced groovy scripts but we don't want to run them via regular IJ groovy script runners.
    // Gradle tooling api should be used for running gradle tasks instead. IJ execution sub-system operates on Location objects
    // which encapsulate PsiElement and groovy runners are automatically applied if that PsiElement IS-A GroovyFile.
    PsiFile file = PsiFileFactory.getInstance(myProject).createFileFromText(name, PlainTextFileType.INSTANCE, "nichts");

    return new ExternalSystemTaskLocation(myProject, file, task);
  }
}
