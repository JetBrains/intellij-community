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

package com.intellij.execution.impl;

import com.intellij.execution.*;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.options.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author dyoma
 */
class ConfigurationSettingsEditor extends CompositeSettingsEditor<RunnerAndConfigurationSettings> {
  private final ArrayList<SettingsEditor<RunnerAndConfigurationSettings>> myRunnerEditors =
    new ArrayList<SettingsEditor<RunnerAndConfigurationSettings>>();
  private RunnersEditorComponent myRunnersComponent;
  private final RunConfiguration myConfiguration;
  private final SettingsEditor<RunConfiguration> myConfigurationEditor;
  private SettingsEditorGroup<RunnerAndConfigurationSettings> myCompound;

  public CompositeSettingsBuilder<RunnerAndConfigurationSettings> getBuilder() {
    init();
    return new GroupSettingsBuilder<RunnerAndConfigurationSettings>(myCompound);
  }

  private void init() {
    if (myCompound == null) {
      myCompound = new SettingsEditorGroup<RunnerAndConfigurationSettings>();
      Disposer.register(this, myCompound);
      if (myConfigurationEditor instanceof SettingsEditorGroup) {
        SettingsEditorGroup<RunConfiguration> group = (SettingsEditorGroup<RunConfiguration>)myConfigurationEditor;
        List<Pair<String, SettingsEditor<RunConfiguration>>> editors = group.getEditors();
        for (Pair<String, SettingsEditor<RunConfiguration>> pair : editors) {
          myCompound.addEditor(pair.getFirst(), new ConfigToSettingsWrapper(pair.getSecond()));
        }
      }
      else {
        myCompound.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"),
                             new ConfigToSettingsWrapper(myConfigurationEditor));
      }


      myRunnersComponent = new RunnersEditorComponent();
      ProgramRunner[] runners = RunnerRegistry.getInstance().getRegisteredRunners();

      final Executor[] executors = ExecutorRegistry.getInstance().getRegisteredExecutors();
      for (final Executor executor : executors) {
        for (ProgramRunner runner : runners) {
          if (runner.canRun(executor.getId(), myConfiguration)) {
            JComponent perRunnerSettings = createCompositePerRunnerSettings(executor, runner);
            if (perRunnerSettings != null) {
              myRunnersComponent.addExecutorComponent(executor, perRunnerSettings);
            }
          }
        }
      }

      if (myRunnerEditors.size() > 0) {
        myCompound.addEditor(ExecutionBundle.message("run.configuration.startup.connection.rab.title"),
                             new CompositeSettingsEditor<RunnerAndConfigurationSettings>(getFactory()) {
                               public CompositeSettingsBuilder<RunnerAndConfigurationSettings> getBuilder() {
                                 return new CompositeSettingsBuilder<RunnerAndConfigurationSettings>() {
                                   public Collection<SettingsEditor<RunnerAndConfigurationSettings>> getEditors() {
                                     return myRunnerEditors;
                                   }

                                   public JComponent createCompoundEditor() {
                                     return myRunnersComponent.getComponent();
                                   }
                                 };
                               }
                             });
      }
    }
  }

  private JComponent createCompositePerRunnerSettings(final Executor executor, final ProgramRunner runner) {
    final SettingsEditor<JDOMExternalizable> configEditor = myConfiguration.getRunnerSettingsEditor(runner);
    SettingsEditor<JDOMExternalizable> runnerEditor;

    try {
      runnerEditor = runner.getSettingsEditor(executor, myConfiguration);
    }
    catch (AbstractMethodError error) {
      // this is stub code for plugin copatibility!
      runnerEditor = null;
    }

    if (configEditor == null && runnerEditor == null) return null;
    SettingsEditor<RunnerAndConfigurationSettings> wrappedConfigEditor = null;
    SettingsEditor<RunnerAndConfigurationSettings> wrappedRunEditor = null;
    if (configEditor != null) {
      wrappedConfigEditor = new SettingsEditorWrapper<RunnerAndConfigurationSettings, JDOMExternalizable>(configEditor,
                                          new Convertor<RunnerAndConfigurationSettings, JDOMExternalizable>() {
                                            public JDOMExternalizable convert(RunnerAndConfigurationSettings configurationSettings) {
                                              return configurationSettings.getConfigurationSettings(runner).getSettings();
                                            }
                                          });
      myRunnerEditors.add(wrappedConfigEditor);
      Disposer.register(this, wrappedConfigEditor);
    }

    if (runnerEditor != null) {
      wrappedRunEditor = new SettingsEditorWrapper<RunnerAndConfigurationSettings, JDOMExternalizable>(runnerEditor,
                                         new Convertor<RunnerAndConfigurationSettings, JDOMExternalizable>() {
                                           public JDOMExternalizable convert(RunnerAndConfigurationSettings configurationSettings) {
                                             return configurationSettings.getRunnerSettings(runner).getData();
                                           }
                                         });
      myRunnerEditors.add(wrappedRunEditor);
      Disposer.register(this, wrappedRunEditor);
    }

    if (wrappedRunEditor != null && wrappedConfigEditor != null) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(wrappedConfigEditor.getComponent(), BorderLayout.CENTER);
      panel.add(wrappedRunEditor.getComponent(), BorderLayout.SOUTH);
      return panel;
    }

    if (wrappedRunEditor != null) return wrappedRunEditor.getComponent();
    return wrappedConfigEditor.getComponent();
  }

  public ConfigurationSettingsEditor(RunnerAndConfigurationSettings settings) {
    super(settings.createFactory());
    myConfigurationEditor = (SettingsEditor<RunConfiguration>)settings.getConfiguration().getConfigurationEditor();
    Disposer.register(this, myConfigurationEditor);
    myConfiguration = settings.getConfiguration();
  }

  public RunnerAndConfigurationSettings getSnapshot() throws ConfigurationException {
    RunnerAndConfigurationSettings settings = getFactory().create();
    settings.setName(myConfiguration.getName());
    if (myConfigurationEditor instanceof CheckableRunConfigurationEditor) {
      ((CheckableRunConfigurationEditor)myConfigurationEditor).checkEditorData(settings.getConfiguration());
    }
    else {
      applyTo(settings);
    }
    return settings;
  }

  private static class RunnersEditorComponent {
    @NonNls private static final String NO_RUNNER_COMPONENT = "<NO RUNNER LABEL>";

    private JList myRunnersList;
    private JPanel myRunnerPanel;
    private final CardLayout myLayout = new CardLayout();
    private final DefaultListModel myListModel = new DefaultListModel();
    private final JLabel myNoRunner = new JLabel(ExecutionBundle.message("run.configuration.norunner.selected.label"));
    private JPanel myRunnersPanel;

    public RunnersEditorComponent() {
      myRunnerPanel.setLayout(myLayout);
      myRunnerPanel.add(myNoRunner, NO_RUNNER_COMPONENT);
      myRunnersList.setModel(myListModel);
      myRunnersList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          updateRunnerComponent();
        }
      });
      updateRunnerComponent();
      myRunnersList.setCellRenderer(new ColoredListCellRenderer() {
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          Executor executor = (Executor)value;
          setIcon(executor.getIcon());
          append(executor.getId(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      });
    }

    private void updateRunnerComponent() {
      Executor executor = (Executor)myRunnersList.getSelectedValue();
      myLayout.show(myRunnerPanel, executor != null ? executor.getId() : NO_RUNNER_COMPONENT);
      myRunnersPanel.revalidate();
    }

    public void addExecutorComponent(Executor executor, JComponent component) {
      myRunnerPanel.add(component, executor.getId());
      myListModel.addElement(executor);
      ListScrollingUtil.ensureSelectionExists(myRunnersList);
    }

    public JComponent getComponent() {
      return myRunnersPanel;
    }
  }

  private static class ConfigToSettingsWrapper extends SettingsEditor<RunnerAndConfigurationSettings> {
    private final SettingsEditor<RunConfiguration> myConfigEditor;

    public ConfigToSettingsWrapper(SettingsEditor<RunConfiguration> configEditor) {
      myConfigEditor = configEditor;
    }

    public void resetEditorFrom(RunnerAndConfigurationSettings configurationSettings) {
      myConfigEditor.resetFrom(configurationSettings.getConfiguration());
    }

    public void applyEditorTo(RunnerAndConfigurationSettings configurationSettings) throws ConfigurationException {
      myConfigEditor.applyTo(configurationSettings.getConfiguration());
    }

    @NotNull
    public JComponent createEditor() {
      return myConfigEditor.getComponent();
    }

    public void disposeEditor() {
      Disposer.dispose(myConfigEditor);
    }
  }
}
