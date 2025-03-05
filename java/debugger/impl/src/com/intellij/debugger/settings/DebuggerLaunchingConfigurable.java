// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.settings;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.memory.agent.MemoryAgentUtil;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.components.JBBox;
import com.intellij.ui.components.panels.VerticalBox;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

class DebuggerLaunchingConfigurable implements ConfigurableUi<DebuggerSettings> {
  private JRadioButton myRbSocket;
  private JRadioButton myRbShmem;
  private JCheckBox myCbShowAlternativeSource;
  private JCheckBox myCbKillImmediately;
  private JCheckBox myCbAlwaysDebug;
  private JCheckBox myCbEnableMemoryAgent;

  @Override
  public void reset(@NotNull DebuggerSettings settings) {
    if (!SystemInfo.isWindows) {
      myRbSocket.setSelected(true);
      myRbShmem.setEnabled(false);
    }
    else {
      if (settings.getTransport() == DebuggerSettings.SHMEM_TRANSPORT) {
        myRbShmem.setSelected(true);
      }
      else {
        myRbSocket.setSelected(true);
      }
      myRbShmem.setEnabled(true);
    }
    myCbShowAlternativeSource.setSelected(settings.SHOW_ALTERNATIVE_SOURCE);
    myCbKillImmediately.setSelected(settings.KILL_PROCESS_IMMEDIATELY);
    myCbAlwaysDebug.setSelected(settings.ALWAYS_DEBUG);
    myCbEnableMemoryAgent.setSelected(settings.ENABLE_MEMORY_AGENT);
  }

  @Override
  public void apply(@NotNull DebuggerSettings settings) {
    getSettingsTo(settings);
  }

  private void getSettingsTo(DebuggerSettings settings) {
    settings.setTransport(myRbShmem.isSelected() ? DebuggerSettings.SHMEM_TRANSPORT : DebuggerSettings.SOCKET_TRANSPORT);
    settings.SHOW_ALTERNATIVE_SOURCE = myCbShowAlternativeSource.isSelected();
    settings.KILL_PROCESS_IMMEDIATELY = myCbKillImmediately.isSelected();
    settings.ALWAYS_DEBUG = myCbAlwaysDebug.isSelected();
    settings.ENABLE_MEMORY_AGENT = myCbEnableMemoryAgent.isSelected();
  }

  @Override
  public boolean isModified(@NotNull DebuggerSettings currentSettings) {
    DebuggerSettings debuggerSettings = currentSettings.clone();
    getSettingsTo(debuggerSettings);
    return !debuggerSettings.equals(currentSettings);
  }

  @Override
  public @NotNull JComponent getComponent() {
    myCbShowAlternativeSource = new JCheckBox(JavaDebuggerBundle.message("label.debugger.general.configurable.show.alternative.source"));
    myRbSocket = new JRadioButton(JavaDebuggerBundle.message("label.debugger.launching.configurable.socket"));
    myRbShmem = new JRadioButton(JavaDebuggerBundle.message("label.debugger.launching.configurable.shmem"));
    myCbKillImmediately = new JCheckBox(JavaDebuggerBundle.message("label.debugger.general.configurable.kill.immediately"));
    myCbAlwaysDebug = new JCheckBox(JavaDebuggerBundle.message("label.debugger.general.configurable.always.debug"));
    myCbEnableMemoryAgent = new JCheckBox(JavaDebuggerBundle.message("label.debugger.general.configurable.enable.memory.agent"));
    myCbEnableMemoryAgent.setToolTipText(JavaDebuggerBundle.message("label.debugger.general.configurable.enable.memory.agent.tooltip.text"));

    final ButtonGroup gr = new ButtonGroup();
    gr.add(myRbSocket);
    gr.add(myRbShmem);
    final JBBox box = JBBox.createHorizontalBox();
    box.add(Box.createRigidArea(JBUI.size(UIUtil.DEFAULT_HGAP, 0)));
    box.add(myRbSocket);
    box.add(Box.createRigidArea(JBUI.size(UIUtil.DEFAULT_HGAP, 0)));
    box.add(myRbShmem);
    final JPanel transportPanel = new JPanel(new BorderLayout());
    transportPanel.add(new JLabel(JavaDebuggerBundle.message("label.debugger.launching.configurable.debugger.transport")), BorderLayout.WEST);
    transportPanel.add(box, BorderLayout.CENTER);

    VerticalBox panel = new VerticalBox();
    panel.setOpaque(false);
    panel.add(transportPanel);
    panel.add(myCbShowAlternativeSource);
    panel.add(myCbKillImmediately);
    if (MemoryAgentUtil.isPlatformSupported()) {
      panel.add(myCbEnableMemoryAgent);
    }
    if (Registry.is("execution.java.always.debug")) {
      panel.add(myCbAlwaysDebug);
    }

    JPanel result = new JPanel(new BorderLayout());
    result.add(panel, BorderLayout.NORTH);
    return result;
  }
}
