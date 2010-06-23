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
package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.ui.components.panels.VerticalBox;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class DebuggerLaunchingConfigurable implements Configurable{
  private JRadioButton myRbSocket;
  private JRadioButton myRbShmem;
  private JCheckBox myHideDebuggerCheckBox;
  private StateRestoringCheckBox myCbForceClassicVM;
  private JCheckBox myCbDisableJIT;
  private JCheckBox myFocusAppCheckBox;

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
    myHideDebuggerCheckBox.setSelected(settings.HIDE_DEBUGGER_ON_PROCESS_TERMINATION);
    myCbForceClassicVM.setSelected(settings.FORCE_CLASSIC_VM);
    myCbDisableJIT.setSelected(settings.DISABLE_JIT);
    myFocusAppCheckBox.setSelected(Registry.is("debugger.mayBringFrameToFrontOnBreakpoint"));
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
    settings.HIDE_DEBUGGER_ON_PROCESS_TERMINATION = myHideDebuggerCheckBox.isSelected();
    settings.FORCE_CLASSIC_VM = myCbForceClassicVM.isSelectedWhenSelectable();
    settings.DISABLE_JIT = myCbDisableJIT.isSelected();
    Registry.get("debugger.mayBringFrameToFrontOnBreakpoint").setValue(myFocusAppCheckBox.isSelected());
  }

  public boolean isModified() {
    final DebuggerSettings currentSettings = DebuggerSettings.getInstance();
    final DebuggerSettings debuggerSettings = currentSettings.clone();
    getSettingsTo(debuggerSettings);
    return !debuggerSettings.equals(currentSettings) && Registry.is("debugger.mayBringFrameToFrontOnBreakpoint") != myFocusAppCheckBox.isSelected();
  }

  public String getDisplayName() {
    return DebuggerBundle.message("debugger.launching.configurable.display.name");
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "reference.idesettings.debugger.launching";
  }

  public JComponent createComponent() {
    myCbForceClassicVM = new StateRestoringCheckBox(DebuggerBundle.message("label.debugger.launching.configurable.force.classic.vm"));
    myCbDisableJIT = new JCheckBox(DebuggerBundle.message("label.debugger.launching.configurable.disable.jit"));
    myHideDebuggerCheckBox = new JCheckBox(DebuggerBundle.message("label.debugger.launching.configurable.hide.window"));
    myRbSocket = new JRadioButton(DebuggerBundle.message("label.debugger.launching.configurable.socket"));
    myRbShmem = new JRadioButton(DebuggerBundle.message("label.debugger.launching.configurable.shmem"));
    myFocusAppCheckBox = new JCheckBox(DebuggerBundle.message("label.debugger.focusAppOnBreakpoint"));

    int cbLeftOffset = 0;
    final Border border = myCbForceClassicVM.getBorder();
    if (border != null) {
      final Insets insets = border.getBorderInsets(myCbForceClassicVM);
      if (insets != null) {
        cbLeftOffset = insets.left;
      }
    }

    final ButtonGroup gr = new ButtonGroup();
    gr.add(myRbSocket);
    gr.add(myRbShmem);
    final Box box = Box.createHorizontalBox();
    box.add(myRbSocket);
    box.add(myRbShmem);
    final JPanel transportPanel = new JPanel(new BorderLayout());
    transportPanel.add(new JLabel(DebuggerBundle.message("label.debugger.launching.configurable.debugger.transport")), BorderLayout.WEST);
    transportPanel.add(box, BorderLayout.CENTER);

    VerticalBox panel = new VerticalBox();
    panel.setOpaque(false);
    panel.add(transportPanel);
    panel.add(myCbForceClassicVM);
    panel.add(myCbDisableJIT);
    panel.add(myHideDebuggerCheckBox);
    panel.add(myFocusAppCheckBox);

    JPanel result = new JPanel(new BorderLayout());
    result.add(panel, BorderLayout.NORTH);

    result.setBorder(new EmptyBorder(4, 4, 0, 0));

    return result;
  }


  public void disposeUIResources() {
  }

}
