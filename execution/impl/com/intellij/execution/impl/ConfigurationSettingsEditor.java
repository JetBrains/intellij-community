package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionRegistry;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RunnerInfo;
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
class ConfigurationSettingsEditor extends CompositeSettingsEditor<RunnerAndConfigurationSettingsImpl> {
  private ArrayList<SettingsEditor<RunnerAndConfigurationSettingsImpl>> myRunnerEditors = new ArrayList<SettingsEditor<RunnerAndConfigurationSettingsImpl>>();
  private RunnersEditorComponent myRunnersComponent;
  private RunConfiguration myConfiguration;
  private final SettingsEditor<RunConfiguration> myConfigurationEditor;
  private SettingsEditorGroup<RunnerAndConfigurationSettingsImpl> myCompound;

  public CompositeSettingsBuilder<RunnerAndConfigurationSettingsImpl> getBuilder() {
    init();
    return new GroupSettingsBuilder<RunnerAndConfigurationSettingsImpl>(myCompound);
  }

  private void init() {
    if (myCompound == null) {
      myCompound = new SettingsEditorGroup<RunnerAndConfigurationSettingsImpl>();
      if (myConfigurationEditor instanceof SettingsEditorGroup) {
        SettingsEditorGroup<RunConfiguration> group = (SettingsEditorGroup<RunConfiguration>)myConfigurationEditor;
        List<Pair<String, SettingsEditor<RunConfiguration>>> editors = group.getEditors();
        for (Pair<String, SettingsEditor<RunConfiguration>> pair : editors) {
          myCompound.addEditor(pair.getFirst(), new ConfigToSettingsWrapper(pair.getSecond()));
        }
      }
      else {
        myCompound.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), new ConfigToSettingsWrapper(myConfigurationEditor));
      }


      myRunnersComponent = new RunnersEditorComponent();
      JavaProgramRunner[] runners = ExecutionRegistry.getInstance().getRegisteredRunners();
      for (JavaProgramRunner runner : runners) {
        JComponent perRunnerSettings = createCompositePerRunnerSettings(runner);
        if (perRunnerSettings != null) {
          myRunnersComponent.addRunnerComponent(runner, perRunnerSettings);
        }
      }

      if (myRunnerEditors.size() > 0) {
        myCompound.addEditor(ExecutionBundle.message("run.configuration.startup.connection.rab.title"), new CompositeSettingsEditor<RunnerAndConfigurationSettingsImpl>(getFactory()) {
          public CompositeSettingsBuilder<RunnerAndConfigurationSettingsImpl> getBuilder() {
            return new CompositeSettingsBuilder<RunnerAndConfigurationSettingsImpl>() {
              public Collection<SettingsEditor<RunnerAndConfigurationSettingsImpl>> getEditors() {
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

  private JComponent createCompositePerRunnerSettings(final JavaProgramRunner runner) {
    final SettingsEditor<JDOMExternalizable> configEditor = myConfiguration.getRunnerSettingsEditor(runner);
    SettingsEditor<JDOMExternalizable> runnerEditor;

    try {
      runnerEditor = runner.getSettingsEditor(myConfiguration);
    } catch(AbstractMethodError error) {
      // this is stub code for plugin copatibility!
      runnerEditor = null;
    }

    if (configEditor == null && runnerEditor == null) return null;
    SettingsEditor<RunnerAndConfigurationSettingsImpl> wrappedConfigEditor = null;
    SettingsEditor<RunnerAndConfigurationSettingsImpl> wrappedRunEditor = null;
    if (configEditor != null) {
      wrappedConfigEditor = new SettingsEditorWrapper<RunnerAndConfigurationSettingsImpl, JDOMExternalizable>(configEditor, new Convertor<RunnerAndConfigurationSettingsImpl,
          JDOMExternalizable>() {
        public JDOMExternalizable convert(RunnerAndConfigurationSettingsImpl configurationSettings) {
          return configurationSettings.getConfigurationSettings(runner).getSettings();
        }
      });
      myRunnerEditors.add(wrappedConfigEditor);
      Disposer.register(this, wrappedConfigEditor);
    }

    if (runnerEditor != null) {
      wrappedRunEditor = new SettingsEditorWrapper<RunnerAndConfigurationSettingsImpl, JDOMExternalizable>(runnerEditor, new Convertor<RunnerAndConfigurationSettingsImpl,
          JDOMExternalizable>(){
        public JDOMExternalizable convert(RunnerAndConfigurationSettingsImpl configurationSettings) {
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

  public ConfigurationSettingsEditor(RunnerAndConfigurationSettingsImpl settings) {
    super(settings.createFactory());
    myConfigurationEditor = (SettingsEditor<RunConfiguration>)settings.getConfiguration().getConfigurationEditor();
    Disposer.register(this, myConfigurationEditor);
    myConfiguration = settings.getConfiguration();
  }

  public RunnerAndConfigurationSettingsImpl getSnapshot() throws ConfigurationException {
    RunnerAndConfigurationSettingsImpl settings = getFactory().create();
    if (myConfigurationEditor instanceof CheckableRunConfigurationEditor){
      ((CheckableRunConfigurationEditor)myConfigurationEditor).checkEditorData(settings.getConfiguration());
    } else {
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
          JavaProgramRunner runner = (JavaProgramRunner)value;
          RunnerInfo info = runner.getInfo();
          setIcon(info.getIcon());
          append(info.getId(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      });
    }

    private void updateRunnerComponent() {
      JavaProgramRunner runner = (JavaProgramRunner)myRunnersList.getSelectedValue();
      myLayout.show(myRunnerPanel, runner != null ? runner.getInfo().getId() : NO_RUNNER_COMPONENT);
      myRunnersPanel.revalidate();
    }

    public void addRunnerComponent(JavaProgramRunner runner, JComponent component) {
      myRunnerPanel.add(component, runner.getInfo().getId());
      myListModel.addElement(runner);
      ListScrollingUtil.ensureSelectionExists(myRunnersList);
    }

    public JComponent getComponent() {
      return myRunnersPanel;
    }
  }

  private static class ConfigToSettingsWrapper extends SettingsEditor<RunnerAndConfigurationSettingsImpl> {
    private final SettingsEditor<RunConfiguration> myConfigEditor;

    public ConfigToSettingsWrapper(SettingsEditor<RunConfiguration> configEditor) {
      myConfigEditor = configEditor;
    }

    public void resetEditorFrom(RunnerAndConfigurationSettingsImpl configurationSettings) {
      myConfigEditor.resetFrom(configurationSettings.getConfiguration());
    }

    public void applyEditorTo(RunnerAndConfigurationSettingsImpl configurationSettings) throws ConfigurationException {
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
