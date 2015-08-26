/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.debugger.impl;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.xdebugger.impl.settings.DebuggerConfigurable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class GenericDebuggerParametersRunnerConfigurable extends SettingsEditor<GenericDebuggerRunnerSettings> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.GenericDebuggerParametersRunnerConfigurable");
  private JPanel myPanel;
  private JTextField myAddressField;
  private JPanel myShMemPanel;
  private JPanel myPortPanel;
  private JTextField myPortField;
  private boolean myIsLocal = false;
  private JButton myDebuggerSettings;
  private JRadioButton mySocketTransport;
  private JRadioButton myShmemTransport;
  private JPanel myTransportPanel;

  public GenericDebuggerParametersRunnerConfigurable(final Project project) {
    myDebuggerSettings.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, DebuggerConfigurable.class);
        if (myIsLocal) {
          setTransport(DebuggerSettings.getInstance().DEBUGGER_TRANSPORT);
        }
        suggestAvailablePortIfNotSpecified();
        updateUI();
      }
    });

    final ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        suggestAvailablePortIfNotSpecified();
        updateUI();
        myPanel.repaint();
      }
    };
    mySocketTransport.addActionListener(listener);
    myShmemTransport.addActionListener(listener);

    updateUI();

    myTransportPanel.setVisible(false);

    ButtonGroup group = new ButtonGroup();
    group.add(mySocketTransport);
    group.add(myShmemTransport);
  }

  private boolean isSocket() {
    return getTransport() == DebuggerSettings.SOCKET_TRANSPORT;
  }

  @Override
  @NotNull
  public JComponent createEditor() {
    return myPanel;
  }

  private void updateUI() {
    myPortPanel.setVisible(isSocket());
    myShMemPanel.setVisible(!isSocket());
    myAddressField.setEditable(!myIsLocal);
    mySocketTransport.setEnabled(!myIsLocal);
    myShmemTransport.setEnabled(!myIsLocal);
  }

  @Override
  public void resetEditorFrom(GenericDebuggerRunnerSettings runnerSettings) {
    setIsLocal(runnerSettings.LOCAL);
    setTransport(runnerSettings.getTransport());
    setPort(StringUtil.notNullize(runnerSettings.getDebugPort()));
    suggestAvailablePortIfNotSpecified();
    updateUI();
  }

  private void suggestAvailablePortIfNotSpecified() {
    String port = getPort();
    boolean portSpecified = !StringUtil.isEmpty(port);
    boolean isSocketTransport = getTransport() == DebuggerSettings.SOCKET_TRANSPORT;
    if (isSocketTransport) {
      try {
        Integer.parseInt(port);
      }
      catch (NumberFormatException ignored) {
        portSpecified = false;
      }
    }

    if (!portSpecified) {
      try {
        setPort(DebuggerUtils.getInstance().findAvailableDebugAddress(isSocketTransport));
      }
      catch (ExecutionException e) {
        LOG.info(e);
      }
    }
  }

  private int getTransport() {
    if (myIsLocal) {
      return DebuggerSettings.getInstance().DEBUGGER_TRANSPORT;
    }
    else {
      return mySocketTransport.isSelected() ? DebuggerSettings.SOCKET_TRANSPORT : DebuggerSettings.SHMEM_TRANSPORT;
    }
  }

  private String getPort() {
    if (isSocket()) {
      return String.valueOf(myPortField.getText());
    }
    else {
      return myAddressField.getText();
    }
  }

  private void checkPort() throws ConfigurationException {
    if (isSocket() && parsePort() == 0) {
      throw new ConfigurationException(DebuggerBundle.message("error.text.invalid.port"));
    }
  }
  private int parsePort() {
    try {
      return Math.max(0, Integer.parseInt(myPortField.getText()));
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  private void setTransport(int transport) {
    mySocketTransport.setSelected(transport == DebuggerSettings.SOCKET_TRANSPORT);
    myShmemTransport.setSelected(transport != DebuggerSettings.SOCKET_TRANSPORT);
  }

  private void setIsLocal(boolean b) {
    myTransportPanel.setVisible(true);
    myDebuggerSettings.setVisible(b);
    myIsLocal = b;
  }

  private void setPort(String port) {
    if (isSocket()) {
      myPortField.setText(String.valueOf(StringUtilRt.parseInt(port, 0)));
    }
    else {
      myAddressField.setText(port);
    }
  }

  @Override
  public void applyEditorTo(GenericDebuggerRunnerSettings runnerSettings) throws ConfigurationException {
    runnerSettings.LOCAL = myIsLocal;
    checkPort();
    runnerSettings.setDebugPort(getPort());
    if (!myIsLocal) {
      runnerSettings.setTransport(getTransport());
    }
  }
}