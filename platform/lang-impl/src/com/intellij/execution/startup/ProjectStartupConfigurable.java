/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.startup;

import com.intellij.execution.*;
import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import com.intellij.execution.actions.ExecutorProvider;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.execution.impl.NewRunConfigurationPopup;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.ui.table.JBTable;
import com.intellij.util.IconUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author Irina.Chernushina on 8/19/2015.
 */
public class ProjectStartupConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final Project myProject;
  private JBTable myTable;
  private ToolbarDecorator myDecorator;
  private ProjectStartupTasksTableModel myModel;

  public ProjectStartupConfigurable(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public String getId() {
    return "preferences.startup.tasks";
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Startup Tasks";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return "reference.settings.startup.tasks";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    myModel = new ProjectStartupTasksTableModel();
    myTable = new JBTable(myModel);
    myTable.getEmptyText().setText("Add run configurations with the + button");
    new TableSpeedSearch(myTable);
    DefaultCellEditor defaultEditor = (DefaultCellEditor)myTable.getDefaultEditor(Object.class);
    defaultEditor.setClickCountToStart(1);

    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    new DumbAwareAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final int row = myTable.getSelectedRow();
        if (row >= 0 && myModel.isCellEditable(row, ProjectStartupTasksTableModel.IS_SHARED_COLUMN)) {
          myModel.setValueAt(!Boolean.TRUE.equals(myTable.getValueAt(row, ProjectStartupTasksTableModel.IS_SHARED_COLUMN)),
                             row, ProjectStartupTasksTableModel.IS_SHARED_COLUMN);
        }
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyEvent.VK_SPACE), myTable);
    myTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() >= 2) {
          editRunConfiguration();
        }
      }
    });

    installRenderers();
    myDecorator = ToolbarDecorator.createDecorator(myTable)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          selectAndAddConfiguration(button);
        }
      })
      .setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          editRunConfiguration();
        }
      })
      .setEditActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          return myTable.getSelectedRow() >= 0;
        }
      })
      .disableUpAction().disableDownAction();

    final JPanel tasksPanel = myDecorator.createPanel();
    final JLabel label = new JLabel("Run tasks and tools via run configurations");
    label.setForeground(UIUtil.getInactiveTextColor());
    label.setHorizontalAlignment(SwingConstants.RIGHT);
    final JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(new JLabel("To be started on project opening:"), BorderLayout.WEST);
    wrapper.add(label, BorderLayout.EAST);
    wrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, UIUtil.DEFAULT_VGAP, 0));

    final JPanel main = new JPanel(new BorderLayout());
    main.add(wrapper, BorderLayout.NORTH);
    main.add(tasksPanel, BorderLayout.CENTER);

    return main;
  }

  private void editRunConfiguration() {
    final int row = myTable.getSelectedRow();
    if (row < 0) return;
    final RunnerAndConfigurationSettings selected = myModel.getAllConfigurations().get(row);

    final RunManager runManager = RunManager.getInstance(myProject);
    final RunnerAndConfigurationSettings was = runManager.getSelectedConfiguration();
    try {
      runManager.setSelectedConfiguration(selected);
      new EditConfigurationsDialog(myProject).showAndGet();
    } finally {
      runManager.setSelectedConfiguration(was);
    }
    myModel.fireTableDataChanged();
    refreshDataUpdateSelection(selected);
  }

  private void refreshDataUpdateSelection(RunnerAndConfigurationSettings settings) {
    if (myTable.isEmpty()) return;
    myModel.reValidateConfigurations(new Processor<RunnerAndConfigurationSettings>() {
      private RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(myProject);
      @Override
      public boolean process(RunnerAndConfigurationSettings settings) {
        return runManager.getConfigurationById(settings.getUniqueID()) != null;
      }
    });

    if (settings != null) {
      final List<RunnerAndConfigurationSettings> configurations = myModel.getAllConfigurations();
      for (int i = 0; i < configurations.size(); i++) {
        final RunnerAndConfigurationSettings configuration = configurations.get(i);
        if (configuration == settings) {
          TableUtil.selectRows(myTable, new int[]{i});
          return;
        }
      }
    }
    TableUtil.selectRows(myTable, new int[]{0});
    myTable.getSelectionModel().setLeadSelectionIndex(0);
  }

  private ChooseRunConfigurationPopup.ItemWrapper<Void> createNewWrapper(final AnActionButton button) {
    return new ChooseRunConfigurationPopup.ItemWrapper<Void>(null) {
      @Override
      public Icon getIcon() {
        return IconUtil.getAddIcon();
      }

      @Override
      public String getText() {
        return UIUtil.removeMnemonic(ExecutionBundle.message("add.new.run.configuration.action2.name"));
      }

      @Override
      public void perform(@NotNull final Project project, @NotNull final Executor executor, @NotNull DataContext context) {
        final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
        final Condition<ConfigurationType> filter = new Condition<ConfigurationType>() {
          private final RunnerRegistry myRegistry = RunnerRegistry.getInstance();

          @Override
          public boolean value(ConfigurationType configurationType) {
            ConfigurationFactory factory;
            return ((factory = runManager.getFactory(configurationType.getId(), null)) != null) &&
                   myRegistry.getRunner(executor.getId(), runManager.getConfigurationTemplate(factory).getConfiguration()) != null;
          }
        };
        final ListPopup popup = NewRunConfigurationPopup.createAddPopup(ContainerUtil.filter(runManager.getConfigurationFactoriesWithoutUnknown(), filter), "",
                                                                        factory -> ApplicationManager.getApplication().invokeLater(() -> {
                                                                          final EditConfigurationsDialog dialog = new EditConfigurationsDialog(project, factory);
                                                                          if (dialog.showAndGet()) {
                                                                            ApplicationManager.getApplication().invokeLater(() -> {
                                                                              RunnerAndConfigurationSettings configuration = RunManager.getInstance(project).getSelectedConfiguration();
                                                                              if (configuration != null) {
                                                                                addConfiguration(configuration);
                                                                              }
                                                                            }, ModalityState.any(), project.getDisposed());
                                                                          }
                                                                        }, ModalityState.any(), project.getDisposed()), null, EmptyRunnable.getInstance(), false);
        showPopup(button, popup);
      }

      @Override
      public boolean available(Executor executor) {
        return true;
      }
    };
  }

  private void addConfiguration(RunnerAndConfigurationSettings configuration) {
    if (!ProjectStartupRunner.canBeRun(configuration)) {
      final String message = "Can not add Run Configuration '" + configuration.getName() + "' to Startup Tasks," +
                             " since it can not be started with 'Run' action.";
      final Balloon balloon = JBPopupFactory.getInstance()
        .createHtmlTextBalloonBuilder(message, MessageType.ERROR, null)
        .setHideOnClickOutside(true)
        .setFadeoutTime(3000)
        .setCloseButtonEnabled(true)
        .createBalloon();
      final RelativePoint rp = new RelativePoint(myDecorator.getActionsPanel(), new Point(5, 5));
      balloon.show(rp, Balloon.Position.atLeft);
      return;
    }
    myModel.addConfiguration(configuration);
    refreshDataUpdateSelection(configuration);
  }

  private void selectAndAddConfiguration(final AnActionButton button) {
    final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    final List<ChooseRunConfigurationPopup.ItemWrapper> wrappers = new ArrayList<>();
    wrappers.add(createNewWrapper(button));
    final ChooseRunConfigurationPopup.ItemWrapper[] allSettings =
      ChooseRunConfigurationPopup.createSettingsList(myProject, new ExecutorProvider() {
        @Override
        public Executor getExecutor() {
          return executor;
        }
      }, false);
    final Set<RunnerAndConfigurationSettings> existing = new HashSet<>(myModel.getAllConfigurations());
    for (ChooseRunConfigurationPopup.ItemWrapper setting : allSettings) {
      if (setting.getValue() instanceof RunnerAndConfigurationSettings) {
        final RunnerAndConfigurationSettings settings = (RunnerAndConfigurationSettings)setting.getValue();
        if (!settings.isTemporary() && ProjectStartupRunner.canBeRun(settings) && !existing.contains(settings)) {
          wrappers.add(setting);
        }
      }
    }
    final JBList list = new JBList(wrappers);
    list.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof ChooseRunConfigurationPopup.ItemWrapper) {
          setIcon(((ChooseRunConfigurationPopup.ItemWrapper)value).getIcon());
          append(((ChooseRunConfigurationPopup.ItemWrapper)value).getText());
        }
      }
    });
    final JBPopup popup = JBPopupFactory.getInstance()
      .createListPopupBuilder(list)
      .setItemChoosenCallback(() -> {
        final int index = list.getSelectedIndex();
        if (index < 0) return;
        final ChooseRunConfigurationPopup.ItemWrapper at = (ChooseRunConfigurationPopup.ItemWrapper)list.getModel().getElementAt(index);
        if (at.getValue() instanceof RunnerAndConfigurationSettings) {
          final RunnerAndConfigurationSettings added = (RunnerAndConfigurationSettings)at.getValue();
          addConfiguration(added);
        } else {
          at.perform(myProject, executor, button.getDataContext());
        }
      })
      .createPopup();

    showPopup(button, popup);
  }

  private void showPopup(AnActionButton button, JBPopup popup) {
    final RelativePoint point = button.getPreferredPopupPoint();
    if (point != null) {
      popup.show(point);
    } else {
      popup.showInCenterOf(myDecorator.getActionsPanel());
    }
  }

  @Override
  public boolean isModified() {
    final ProjectStartupTaskManager projectStartupTaskManager = ProjectStartupTaskManager.getInstance(myProject);
    final Set<RunnerAndConfigurationSettings> shared = new HashSet<>(projectStartupTaskManager.getSharedConfigurations());
    final List<RunnerAndConfigurationSettings> list = new ArrayList<>(shared);
    list.addAll(projectStartupTaskManager.getLocalConfigurations());
    Collections.sort(list, ProjectStartupTasksTableModel.RunnerAndConfigurationSettingsComparator.getInstance());

    if (!Comparing.equal(list, myModel.getAllConfigurations())) return true;
    if (!Comparing.equal(shared, myModel.getSharedConfigurations())) return true;
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    final List<RunnerAndConfigurationSettings> shared = new ArrayList<>();
    final List<RunnerAndConfigurationSettings> local = new ArrayList<>();

    final Set<RunnerAndConfigurationSettings> sharedSet = myModel.getSharedConfigurations();
    final List<RunnerAndConfigurationSettings> allConfigurations = myModel.getAllConfigurations();
    for (RunnerAndConfigurationSettings configuration : allConfigurations) {
      if (sharedSet.contains(configuration)) {
        shared.add(configuration);
      } else {
        local.add(configuration);
      }
    }

    ProjectStartupTaskManager.getInstance(myProject).setStartupConfigurations(shared, local);
  }

  @Override
  public void reset() {
    final ProjectStartupTaskManager projectStartupTaskManager = ProjectStartupTaskManager.getInstance(myProject);
    myModel.setData(projectStartupTaskManager.getSharedConfigurations(), projectStartupTaskManager.getLocalConfigurations());
    refreshDataUpdateSelection(null);
  }

  private void installRenderers() {
    final TableColumn checkboxColumn = myTable.getColumnModel().getColumn(ProjectStartupTasksTableModel.IS_SHARED_COLUMN);
    final String header = checkboxColumn.getHeaderValue().toString();
    final FontMetrics fm = myTable.getFontMetrics(myTable.getTableHeader().getFont());
    final int width = - new JBCheckBox().getPreferredSize().width + fm.stringWidth(header + "ww");
    TableUtil.setupCheckboxColumn(checkboxColumn, width);
    checkboxColumn.setCellRenderer(new BooleanTableCellRenderer());

    myTable.getTableHeader().setResizingAllowed(false);
    myTable.getTableHeader().setReorderingAllowed(false);

    final TableColumn nameColumn = myTable.getColumnModel().getColumn(ProjectStartupTasksTableModel.NAME_COLUMN);
    nameColumn.setCellRenderer(new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
        final RunnerAndConfigurationSettings settings = myModel.getAllConfigurations().get(row);
        setIcon(settings.getConfiguration().getIcon());
        append(settings.getName());
      }
    });
  }
}
