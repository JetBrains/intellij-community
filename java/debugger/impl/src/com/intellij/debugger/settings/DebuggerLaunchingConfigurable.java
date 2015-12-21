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
package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.ui.components.panels.VerticalBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

class DebuggerLaunchingConfigurable implements ConfigurableUi<DebuggerSettings> {
  private JRadioButton myRbSocket;
  private JRadioButton myRbShmem;
  private StateRestoringCheckBox myCbForceClassicVM;
  private JCheckBox myCbDisableJIT;
  private JCheckBox myCbShowAlternativeSource;

  @Override
  public void reset(@NotNull DebuggerSettings settings) {
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
    myCbForceClassicVM.setSelected(settings.FORCE_CLASSIC_VM);
    myCbDisableJIT.setSelected(settings.DISABLE_JIT);
    myCbShowAlternativeSource.setSelected(settings.SHOW_ALTERNATIVE_SOURCE);
  }

  @Override
  public void apply(@NotNull DebuggerSettings settings) {
    getSettingsTo(settings);
  }

  private void getSettingsTo(DebuggerSettings settings) {
    if (myRbShmem.isSelected()) {
      settings.DEBUGGER_TRANSPORT = DebuggerSettings.SHMEM_TRANSPORT;
    }
    else {
      settings.DEBUGGER_TRANSPORT = DebuggerSettings.SOCKET_TRANSPORT;
    }
    settings.FORCE_CLASSIC_VM = myCbForceClassicVM.isSelectedWhenSelectable();
    settings.DISABLE_JIT = myCbDisableJIT.isSelected();
    settings.SHOW_ALTERNATIVE_SOURCE = myCbShowAlternativeSource.isSelected();
  }

  @Override
  public boolean isModified(@NotNull DebuggerSettings currentSettings) {
    DebuggerSettings debuggerSettings = currentSettings.clone();
    getSettingsTo(debuggerSettings);
    return !debuggerSettings.equals(currentSettings);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    myCbForceClassicVM = new StateRestoringCheckBox(DebuggerBundle.message("label.debugger.launching.configurable.force.classic.vm"));
    myCbDisableJIT = new JCheckBox(DebuggerBundle.message("label.debugger.launching.configurable.disable.jit"));
    myCbShowAlternativeSource = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.show.alternative.source"));
    myRbSocket = new JRadioButton(DebuggerBundle.message("label.debugger.launching.configurable.socket"));
    myRbShmem = new JRadioButton(DebuggerBundle.message("label.debugger.launching.configurable.shmem"));

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
    panel.add(myCbShowAlternativeSource);

    JPanel result = new JPanel(new BorderLayout());
    result.add(panel, BorderLayout.NORTH);
    return result;
  }
}