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

/*
 * Class RemoteConfigurable
 * @author Jeka
 */
package com.intellij.execution.remote;

import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.ui.ConfigurationArgumentsHelpArea;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.*;

public class RemoteConfigurable extends SettingsEditor<RemoteConfiguration> {
  JPanel myPanel;
  private JRadioButton myRbSocket;
  private JRadioButton myRbShmem;
  private JRadioButton myRbListen;
  private JRadioButton myRbAttach;
  private JTextField myAddressField;
  private JTextField myHostField;
  private JTextField myPortField;
  private JPanel myShmemPanel;
  private JPanel mySocketPanel;
  private ConfigurationArgumentsHelpArea myHelpArea;
  @NonNls private ConfigurationArgumentsHelpArea myJDK13HelpArea;
  private ConfigurationArgumentsHelpArea myJDK14HelpArea;
  private LabeledComponent<ModulesComboBox> myModule;
  private String myHostName = "";
  @NonNls
  protected static final String LOCALHOST = "localhost";
  private final ConfigurationModuleSelector myModuleSelector;

  public RemoteConfigurable(final Project project) {
    myHelpArea.setLabelText(ExecutionBundle.message("remote.configuration.remote.debugging.allows.you.to.connect.idea.to.a.running.jvm.label"));
    myHelpArea.setToolbarVisible();

    myJDK13HelpArea.setLabelText(ExecutionBundle.message("environment.variables.helper.use.arguments.jdk13.label"));
    myJDK13HelpArea.setToolbarVisible();
    myJDK14HelpArea.setLabelText(ExecutionBundle.message("environment.variables.helper.use.arguments.jdk14.label"));
    myJDK14HelpArea.setToolbarVisible();

    final ButtonGroup transportGroup = new ButtonGroup();
    transportGroup.add(myRbSocket);
    transportGroup.add(myRbShmem);

    final ButtonGroup connectionGroup = new ButtonGroup();
    connectionGroup.add(myRbListen);
    connectionGroup.add(myRbAttach);

    final DocumentListener helpTextUpdater = new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        updateHelpText();
      }
    };
    myAddressField.getDocument().addDocumentListener(helpTextUpdater);
    myHostField.getDocument().addDocumentListener(helpTextUpdater);
    myPortField.getDocument().addDocumentListener(helpTextUpdater);
    myRbSocket.setSelected(true);
    final ActionListener listener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final Object source = e.getSource();
        if (source.equals(myRbSocket)) {
           myShmemPanel.setVisible(false);
           mySocketPanel.setVisible(true);
        }
        else if (source.equals(myRbShmem)) {
           myShmemPanel.setVisible(true);
           mySocketPanel.setVisible(false);
        }
        myPanel.repaint();
        updateHelpText();
      }
    };
    myRbShmem.addActionListener(listener);
    myRbSocket.addActionListener(listener);

    final ItemListener updateListener = new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        final boolean isAttach = myRbAttach.isSelected();

        if(!isAttach && myHostField.isEditable()) {
          myHostName = myHostField.getText();
        }

        myHostField.setEditable(isAttach);
        myHostField.setEnabled(isAttach);

        myHostField.setText(isAttach ? myHostName : LOCALHOST);
        updateHelpText();
      }
    };
    myRbAttach.addItemListener(updateListener);
    myRbListen.addItemListener(updateListener);

    final FocusListener fieldFocusListener = new FocusAdapter() {
      public void focusLost(final FocusEvent e) {
        updateHelpText();
      }
    };
    myAddressField.addFocusListener(fieldFocusListener);
    myPortField.addFocusListener(fieldFocusListener);

    myModuleSelector = new ConfigurationModuleSelector(project, myModule.getComponent(), "<whole project>");
  }

  public void applyEditorTo(@NotNull final RemoteConfiguration configuration) throws ConfigurationException {
    configuration.HOST = (myHostField.isEditable() ? myHostField.getText() : myHostName).trim();
    if (configuration.HOST != null && configuration.HOST.isEmpty()) {
      configuration.HOST = null;
    }
    configuration.PORT = myPortField.getText().trim();
    if (configuration.PORT != null && configuration.PORT.isEmpty()) {
      configuration.PORT = null;
    }
    configuration.SHMEM_ADDRESS = myAddressField.getText().trim();
    if (configuration.SHMEM_ADDRESS != null && configuration.SHMEM_ADDRESS.isEmpty()) {
      configuration.SHMEM_ADDRESS = null;
    }
    configuration.USE_SOCKET_TRANSPORT = myRbSocket.isSelected();
    configuration.SERVER_MODE = myRbListen.isSelected();
    myModuleSelector.applyTo(configuration);
  }

  public void resetEditorFrom(final RemoteConfiguration configuration) {
    if (!SystemInfo.isWindows) {
      configuration.USE_SOCKET_TRANSPORT = true;
      myRbShmem.setEnabled(false);
      myAddressField.setEditable(false);
    }
    myAddressField.setText(configuration.SHMEM_ADDRESS);
    myHostName = configuration.HOST;
    myHostField.setText(configuration.HOST);
    myPortField.setText(configuration.PORT);
    if (configuration.USE_SOCKET_TRANSPORT) {
      myRbSocket.doClick();
    }
    else {
      myRbShmem.doClick();
    }
    if (configuration.SERVER_MODE) {
      myRbListen.doClick();
    }
    else {
      myRbAttach.doClick();
    }
    myRbShmem.setEnabled(SystemInfo.isWindows);
    myModuleSelector.reset(configuration);
  }

  @NotNull
  public JComponent createEditor() {
    return myPanel;
  }

  private void updateHelpText() {
    boolean useSockets = !myRbShmem.isSelected();

    final RemoteConnection connection = new RemoteConnection(
      useSockets,
      myHostName,
      useSockets ? myPortField.getText().trim() : myAddressField.getText().trim(),
      myRbListen.isSelected()
    );
    final String cmdLine = connection.getLaunchCommandLine();
    // -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=7007
    final String jvmtiCmdLine = cmdLine.replace("-Xdebug", "").replace("-Xrunjdwp:", "-agentlib:jdwp=").trim();
    myHelpArea.updateText(jvmtiCmdLine);
    myJDK14HelpArea.updateText(cmdLine);
    myJDK13HelpArea.updateText("-Xnoagent -Djava.compiler=NONE " + cmdLine);
  }


}