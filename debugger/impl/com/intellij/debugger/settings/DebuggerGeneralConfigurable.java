package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.ui.classFilter.ClassFilterEditor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class DebuggerGeneralConfigurable implements Configurable{
  private JRadioButton myRbSocket;
  private JRadioButton myRbShmem;
  private JCheckBox myCbStepInfoFiltersEnabled;
  private JCheckBox myCbSkipSyntheticMethods;
  private JCheckBox myCbSkipConstructors;
  private JCheckBox myCbSkipClassLoaders;
  private JCheckBox myHideDebuggerCheckBox;
  private JRadioButton myRbAlways;
  private JRadioButton myRbNever;
  private JRadioButton myRbAsk;
  private StateRestoringCheckBox myCbForceClassicVM;
  private ClassFilterEditor mySteppingFilterEditor;
  private JTextField myValueTooltipDelayField;
  private JCheckBox myCbSkipSimpleGetters;
  private final Project myProject;
  private BaseRenderersConfigurable myBaseRenderersConfigurable;

  public DebuggerGeneralConfigurable(Project project) {
    myProject = project;
    myBaseRenderersConfigurable = new BaseRenderersConfigurable(project);
  }

  public void reset() {
    myBaseRenderersConfigurable.reset();
    final DebuggerSettings settings = DebuggerSettings.getInstance();
    if (!SystemInfo.isWindows) {
      myRbSocket.setSelected(true);
      myRbShmem.setEnabled(false);
    }
    else {
      if (settings.DEBUGGER_TRANSPORT == DebuggerSettings.SHMEM_TRANSPORT) {
        myRbShmem.setSelected(true);
      }
      else {
        myRbSocket.setSelected(true);
      }
      myRbShmem.setEnabled(true);
    }
    myCbSkipSimpleGetters.setSelected(settings.SKIP_GETTERS);
    myCbSkipSyntheticMethods.setSelected(settings.SKIP_SYNTHETIC_METHODS);
    myCbSkipConstructors.setSelected(settings.SKIP_CONSTRUCTORS);
    myCbSkipClassLoaders.setSelected(settings.SKIP_CLASSLOADERS);
    myValueTooltipDelayField.setText(Integer.toString(settings.VALUE_LOOKUP_DELAY));
    myHideDebuggerCheckBox.setSelected(settings.HIDE_DEBUGGER_ON_PROCESS_TERMINATION);
    myCbForceClassicVM.setSelected(settings.FORCE_CLASSIC_VM);

    myCbStepInfoFiltersEnabled.setSelected(settings.TRACING_FILTERS_ENABLED);

    mySteppingFilterEditor.setFilters(settings.getSteppingFilters());
    mySteppingFilterEditor.setEnabled(settings.TRACING_FILTERS_ENABLED);

    if(DebuggerSettings.RUN_HOTSWAP_ALWAYS.equals(settings.RUN_HOTSWAP_AFTER_COMPILE)) {
      myRbAlways.setSelected(true);
    }
    else if(DebuggerSettings.RUN_HOTSWAP_NEVER.equals(settings.RUN_HOTSWAP_AFTER_COMPILE)) {
      myRbNever.setSelected(true);
    }
    else {
      myRbAsk.setSelected(true);
    }
  }

  public void apply() {
    getSettingsTo(DebuggerSettings.getInstance());
    myBaseRenderersConfigurable.apply();
  }

  private void getSettingsTo(DebuggerSettings settings) {
    if (myRbShmem.isSelected()) {
      settings.DEBUGGER_TRANSPORT = DebuggerSettings.SHMEM_TRANSPORT;
    }
    else {
      settings.DEBUGGER_TRANSPORT = DebuggerSettings.SOCKET_TRANSPORT;
    }
    settings.SKIP_GETTERS = myCbSkipSimpleGetters.isSelected();
    settings.SKIP_SYNTHETIC_METHODS = myCbSkipSyntheticMethods.isSelected();
    settings.SKIP_CONSTRUCTORS = myCbSkipConstructors.isSelected();
    settings.SKIP_CLASSLOADERS = myCbSkipClassLoaders.isSelected();
    try {
      settings.VALUE_LOOKUP_DELAY = Integer.parseInt(myValueTooltipDelayField.getText().trim());
    }
    catch (NumberFormatException e) {
    }
    settings.HIDE_DEBUGGER_ON_PROCESS_TERMINATION = myHideDebuggerCheckBox.isSelected();
    settings.FORCE_CLASSIC_VM = myCbForceClassicVM.isSelectedWhenSelectable();
    settings.TRACING_FILTERS_ENABLED = myCbStepInfoFiltersEnabled.isSelected();

    mySteppingFilterEditor.stopEditing();
    settings.setSteppingFilters(mySteppingFilterEditor.getFilters());

    if (myRbAlways.isSelected()) {
      settings.RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_ALWAYS;
    }
    else if (myRbNever.isSelected()) {
      settings.RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_NEVER;
    }
    else {
      settings.RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_ASK;
    }
  }

  public boolean isModified() {
    if (myBaseRenderersConfigurable.isModified()) {
      return true;
    }
    final DebuggerSettings debuggerSettings = new DebuggerSettings(null);
    getSettingsTo(debuggerSettings);
    return !debuggerSettings.equals(DebuggerSettings.getInstance());
  }

  public String getDisplayName() {
    return DebuggerBundle.message("debugger.general.configurable.display.name");
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    final JPanel panel = new JPanel(new GridBagLayout());

    final JComponent generalGroup = createGeneralGroup();
    panel.add(generalGroup, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

    final JComponent launchingGroup = createLaunchingGroup();
    panel.add(launchingGroup, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

    final JComponent baseRenderersGroup = createBaseRenderersGroup();
    panel.add(baseRenderersGroup, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

    final JComponent steppingGroup = createSteppingGroup();
    panel.add(steppingGroup, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

    return panel;
  }

  private JComponent createGeneralGroup() {
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(getDisplayName()));

    myHideDebuggerCheckBox = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.hide.window"));
    panel.add(myHideDebuggerCheckBox, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));

    final JLabel tooltipLabel = new JLabel(DebuggerBundle.message("label.debugger.general.configurable.tooltips.delay"));
    panel.add(tooltipLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    myValueTooltipDelayField = new JTextField(10);
    panel.add(myValueTooltipDelayField, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    tooltipLabel.setLabelFor(myValueTooltipDelayField);

    panel.add(new JLabel(DebuggerBundle.message("label.debugger.general.configurable.reload.classes")), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    myRbAlways = new JRadioButton(DebuggerBundle.message("label.debugger.general.configurable.always"));
    myRbNever = new JRadioButton(DebuggerBundle.message("label.debugger.general.configurable.never"));
    myRbAsk = new JRadioButton(DebuggerBundle.message("label.debugger.general.configurable.ask"));
    final ButtonGroup group = new ButtonGroup();
    group.add(myRbAlways);
    group.add(myRbNever);
    group.add(myRbAsk);
    final Box box = Box.createHorizontalBox();
    box.add(myRbAlways);
    box.add(myRbNever);
    box.add(myRbAsk);
    panel.add(box, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));

    return panel;
  }

  private JComponent createLaunchingGroup() {
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(DebuggerBundle.message("label.debugger.general.configurable.group.launching")));

    myCbForceClassicVM = new StateRestoringCheckBox(DebuggerBundle.message("label.debugger.general.configurable.force.classic.vm"));
    panel.add(myCbForceClassicVM, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));

    panel.add(new JLabel(DebuggerBundle.message("label.debugger.general.configurable.debugger.transport")), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    myRbSocket = new JRadioButton(DebuggerBundle.message("label.debugger.general.configurable.socket"));
    myRbShmem = new JRadioButton(DebuggerBundle.message("label.debugger.general.configurable.shmem"));
    final ButtonGroup gr = new ButtonGroup();
    gr.add(myRbSocket);
    gr.add(myRbShmem);
    final Box box = Box.createHorizontalBox();
    box.add(myRbSocket);
    box.add(myRbShmem);
    panel.add(box, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));

    return panel;
  }

  private JComponent createBaseRenderersGroup() {
    final JComponent component = myBaseRenderersConfigurable.createComponent();
    component.setBorder(IdeBorderFactory.createTitledBorder(
      DebuggerBundle.message("label.debugger.general.configurable.group.base.renderers")));
    return component;
  }

  private JComponent createSteppingGroup() {
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(DebuggerBundle.message("label.debugger.general.configurable.group.stepping")));

    myCbSkipSyntheticMethods = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.skip.synthetic.methods"));
    myCbSkipConstructors = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.skip.constructors"));
    myCbSkipClassLoaders = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.skip.classloaders"));
    myCbSkipSimpleGetters = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.skip.simple.getters"));
    myCbStepInfoFiltersEnabled = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.step.filters.list.header"));
    panel.add(myCbSkipSyntheticMethods, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 4, 0, 0),0, 0));
    panel.add(myCbSkipConstructors, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 4, 0, 0),0, 0));
    panel.add(myCbSkipClassLoaders, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 4, 0, 0),0, 0));
    panel.add(myCbSkipSimpleGetters, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 4, 0, 0),0, 0));
    panel.add(myCbStepInfoFiltersEnabled, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(8, 4, 0, 0),0, 0));

    mySteppingFilterEditor = new ClassFilterEditor(myProject);
    panel.add(mySteppingFilterEditor, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 12, 0, 0),0, 0));

    myCbStepInfoFiltersEnabled.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        mySteppingFilterEditor.setEnabled(myCbStepInfoFiltersEnabled.isSelected());
      }
    });
    return panel;
  }

  public void disposeUIResources() {
    myBaseRenderersConfigurable.disposeUIResources();
  }

}
