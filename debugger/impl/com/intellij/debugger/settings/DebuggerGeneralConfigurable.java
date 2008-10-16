package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class DebuggerGeneralConfigurable implements Configurable{
  private JRadioButton myRbSocket;
  private JRadioButton myRbShmem;
  private JCheckBox myHideDebuggerCheckBox;
  private JCheckBox myHotswapInBackground;
  private JCheckBox myCbCompileBeforeHotswap;
  private JRadioButton myRbAlways;
  private JRadioButton myRbNever;
  private JRadioButton myRbAsk;
  private StateRestoringCheckBox myCbForceClassicVM;
  private JCheckBox myCbDisableJIT;
  private JTextField myValueTooltipDelayField;

  public void reset() {
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
    myValueTooltipDelayField.setText(Integer.toString(settings.VALUE_LOOKUP_DELAY));
    myHideDebuggerCheckBox.setSelected(settings.HIDE_DEBUGGER_ON_PROCESS_TERMINATION);
    myHotswapInBackground.setSelected(settings.HOTSWAP_IN_BACKGROUND);
    myCbCompileBeforeHotswap.setSelected(settings.COMPILE_BEFORE_HOTSWAP);
    myCbForceClassicVM.setSelected(settings.FORCE_CLASSIC_VM);
    myCbDisableJIT.setSelected(settings.DISABLE_JIT);

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
  }

  private void getSettingsTo(DebuggerSettings settings) {
    if (myRbShmem.isSelected()) {
      settings.DEBUGGER_TRANSPORT = DebuggerSettings.SHMEM_TRANSPORT;
    }
    else {
      settings.DEBUGGER_TRANSPORT = DebuggerSettings.SOCKET_TRANSPORT;
    }
    try {
      settings.VALUE_LOOKUP_DELAY = Integer.parseInt(myValueTooltipDelayField.getText().trim());
    }
    catch (NumberFormatException e) {
    }
    settings.HIDE_DEBUGGER_ON_PROCESS_TERMINATION = myHideDebuggerCheckBox.isSelected();
    settings.HOTSWAP_IN_BACKGROUND = myHotswapInBackground.isSelected();
    settings.COMPILE_BEFORE_HOTSWAP = myCbCompileBeforeHotswap.isSelected();
    settings.FORCE_CLASSIC_VM = myCbForceClassicVM.isSelectedWhenSelectable();
    settings.DISABLE_JIT = myCbDisableJIT.isSelected();

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
    final DebuggerSettings currentSettings = DebuggerSettings.getInstance();
    final DebuggerSettings debuggerSettings = currentSettings.clone();
    getSettingsTo(debuggerSettings);
    return !debuggerSettings.equals(currentSettings);
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

    final JComponent launchingGroup = createLaunchingGroup();
    panel.add(launchingGroup, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

    final JComponent generalGroup = createGeneralGroup();
    panel.add(generalGroup, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(10, 0, 0, 0), 0, 0));

    return panel;
  }

  private JComponent createGeneralGroup() {
    final JPanel panel = new JPanel(new GridBagLayout());

    myHideDebuggerCheckBox = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.hide.window"));
    int leftInset = 0;
    final Border border = myHideDebuggerCheckBox.getBorder();
    if (border != null) {
      final Insets insets = border.getBorderInsets(myHideDebuggerCheckBox);
      if (insets != null) {
        leftInset = insets.left;
      }
    }
    panel.add(myHideDebuggerCheckBox, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));


    final JLabel tooltipLabel = new JLabel(DebuggerBundle.message("label.debugger.general.configurable.tooltips.delay"));
    panel.add(tooltipLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, leftInset, 0, 0), 0, 0));
    myValueTooltipDelayField = new JTextField(10);
    panel.add(myValueTooltipDelayField, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    tooltipLabel.setLabelFor(myValueTooltipDelayField);

    myCbCompileBeforeHotswap = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.compile.before.hotswap"));
    panel.add(myCbCompileBeforeHotswap, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));

    panel.add(new JLabel(DebuggerBundle.message("label.debugger.general.configurable.reload.classes")), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, leftInset, 0, 0), 0, 0));
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

    myHotswapInBackground = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.hotswap.background"));
    panel.add(myHotswapInBackground, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));

    return panel;
  }

  private JComponent createLaunchingGroup() {
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(DebuggerBundle.message("label.debugger.general.configurable.group.launching")));

    myCbForceClassicVM = new StateRestoringCheckBox(DebuggerBundle.message("label.debugger.general.configurable.force.classic.vm"));
    panel.add(myCbForceClassicVM, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));

    myCbDisableJIT = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.disable.jit"));
    panel.add(myCbDisableJIT, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));

    myRbSocket = new JRadioButton(DebuggerBundle.message("label.debugger.general.configurable.socket"));
    myRbShmem = new JRadioButton(DebuggerBundle.message("label.debugger.general.configurable.shmem"));
    final ButtonGroup gr = new ButtonGroup();
    gr.add(myRbSocket);
    gr.add(myRbShmem);
    final Box box = Box.createHorizontalBox();
    box.add(myRbSocket);
    box.add(myRbShmem);

    final JPanel transportPanel = new JPanel(new BorderLayout());
    transportPanel.add(new JLabel(DebuggerBundle.message("label.debugger.general.configurable.debugger.transport")), BorderLayout.WEST);
    transportPanel.add(box, BorderLayout.CENTER);
    panel.add(transportPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 6, 0, 0), 0, 0));

    return panel;
  }

  public void disposeUIResources() {
  }

}
