// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.startup;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import com.intellij.execution.actions.ExecutorProvider;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.execution.impl.NewRunConfigurationPopup;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.table.JBTable;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.NamedColorUtil;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ProjectStartupConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  final static class ProjectStartupConfigurableProvider extends ConfigurableProvider {
    private final Project myProject;

    ProjectStartupConfigurableProvider(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public Configurable createConfigurable() {
      return new ProjectStartupConfigurable(myProject);
    }

    @Override
    public boolean canCreateConfigurable() {
      return !PlatformUtils.isDataGrip();
    }
  }

  private final Project myProject;
  private JBTable myTable;
  private ToolbarDecorator myDecorator;
  private ProjectStartupTasksTableModel myModel;

  ProjectStartupConfigurable(@NotNull Project project) {
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
    return ExecutionBundle.message("configurable.ProjectStartupConfigurable.display.name");
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return "reference.settings.startup.tasks";
  }

  @NotNull
  @Override
  public JComponent createComponent() {
    myModel = new ProjectStartupTasksTableModel();
    myTable = new JBTable(myModel);
    myTable.setShowGrid(false);
    myTable.getEmptyText().setText(ExecutionBundle.message("settings.project.startup.add.run.configurations.with.the.button"));
    TableSpeedSearch.installOn(myTable);
    DefaultCellEditor defaultEditor = (DefaultCellEditor)myTable.getDefaultEditor(Object.class);
    defaultEditor.setClickCountToStart(1);

    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    DumbAwareAction.create(e -> {
      int row = myTable.getSelectedRow();
      if (row >= 0 && myModel.isCellEditable(row, ProjectStartupTasksTableModel.IS_SHARED_COLUMN)) {
        myModel.setValueAt(!Boolean.TRUE.equals(myTable.getValueAt(row, ProjectStartupTasksTableModel.IS_SHARED_COLUMN)),
                           row, ProjectStartupTasksTableModel.IS_SHARED_COLUMN);
      }
    }).registerCustomShortcutSet(new CustomShortcutSet(KeyEvent.VK_SPACE), myTable);
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
      .setAddAction(this::selectAndAddConfiguration)
      .setAddIcon(LayeredIcon.ADD_WITH_DROPDOWN)
      .setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          editRunConfiguration();
        }
      })
      .setEditActionUpdater(e -> myTable.getSelectedRow() >= 0)
      .disableUpAction().disableDownAction();

    final JPanel tasksPanel = myDecorator.createPanel();
    final JLabel label = new JLabel(ExecutionBundle.message("settings.project.startup.run.tasks.and.tools.via.run.configurations"));
    label.setForeground(NamedColorUtil.getInactiveTextColor());
    label.setHorizontalAlignment(SwingConstants.RIGHT);
    final JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(new JLabel(ExecutionBundle.message("settings.project.startup.to.be.started.on.project.opening")), BorderLayout.WEST);
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
    myModel.reValidateConfigurations(new Processor<>() {
      private final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(myProject);

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
    return new ChooseRunConfigurationPopup.ItemWrapper<>(null) {
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
        List<ConfigurationType> typesToShow =
          ContainerUtil.filter(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensionList(), configurationType -> {
            ConfigurationFactory factory = runManager.getFactory(configurationType, null);
            return factory != null &&
                   ProgramRunner.getRunner(executor.getId(), runManager.getConfigurationTemplate(factory).getConfiguration()) != null;
          });
        final JBPopup popup = NewRunConfigurationPopup.createAddPopup(project, typesToShow, "",
                                                                      factory -> ApplicationManager.getApplication().invokeLater(() -> {
                                                                        final EditConfigurationsDialog dialog =
                                                                          new EditConfigurationsDialog(project, factory);
                                                                        if (dialog.showAndGet()) {
                                                                          ApplicationManager.getApplication().invokeLater(() -> {
                                                                            RunnerAndConfigurationSettings configuration =
                                                                              RunManager.getInstance(project).getSelectedConfiguration();
                                                                            if (configuration != null) {
                                                                              addConfiguration(configuration);
                                                                            }
                                                                          }, project.getDisposed());
                                                                        }
                                                                      }, project.getDisposed()), null, null, false);
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
      final String message = ExecutionBundle.message("settings.project.startup.warning", configuration.getName());
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
    final List<ChooseRunConfigurationPopup.ItemWrapper<?>> wrappers = new ArrayList<>();
    wrappers.add(createNewWrapper(button));
    final List<ChooseRunConfigurationPopup.ItemWrapper> allSettings =
      ChooseRunConfigurationPopup.createSettingsList(myProject, new ExecutorProvider() {
        @Override
        public Executor getExecutor() {
          return executor;
        }
      }, false);
    final Set<RunnerAndConfigurationSettings> existing = new HashSet<>(myModel.getAllConfigurations());
    for (ChooseRunConfigurationPopup.ItemWrapper<?> setting : allSettings) {
      if (setting.getValue() instanceof RunnerAndConfigurationSettings settings) {
        if (!settings.isTemporary() && ProjectStartupRunner.canBeRun(settings) && !existing.contains(settings)) {
          wrappers.add(setting);
        }
      }
    }
    final JBPopup popup = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(wrappers)
      .setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
        label.setIcon(value.getIcon());
        label.setText(value.getText());
      }))
      .setItemChosenCallback((at) -> {
        if (at.getValue() instanceof RunnerAndConfigurationSettings added) {
          addConfiguration(added);
        }
        else {
          at.perform(myProject, executor, button.getDataContext());
        }
      })
      .createPopup();

    showPopup(button, popup);
  }

  private void showPopup(AnActionButton button, JBPopup popup) {
    final RelativePoint point = button.getPreferredPopupPoint();
    popup.show(point);
  }

  @Override
  public boolean isModified() {
    final ProjectStartupTaskManager projectStartupTaskManager = ProjectStartupTaskManager.getInstance(myProject);
    final Set<RunnerAndConfigurationSettings> shared = new HashSet<>(projectStartupTaskManager.getSharedConfigurations());
    final List<RunnerAndConfigurationSettings> list = new ArrayList<>(shared);
    list.addAll(projectStartupTaskManager.getLocalConfigurations());
    list.sort(ProjectStartupTasksTableModel.RunnerAndConfigurationSettingsComparator.getInstance());

    if (!Comparing.equal(list, myModel.getAllConfigurations())) return true;
    if (!Comparing.equal(shared, myModel.getSharedConfigurations())) return true;
    return false;
  }

  @Override
  public void apply() {
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
      protected void customizeCellRenderer(@NotNull JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
        final RunnerAndConfigurationSettings settings = myModel.getAllConfigurations().get(row);
        setIcon(settings.getConfiguration().getIcon());
        append(settings.getName());
      }
    });
  }
}
