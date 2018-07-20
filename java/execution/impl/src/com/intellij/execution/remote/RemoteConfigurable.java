// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.remote;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.fields.IntegerField;
import com.intellij.ui.components.labels.DropDownLink;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Arrays;

public class RemoteConfigurable extends SettingsEditor<RemoteConfiguration> {
  private enum Mode {
    ATTACH("Attach to remote JVM"),
    LISTEN("Listen to remote JVM");

    private final String text;
    Mode(String text) {
      this.text = text;
    }

    @Override
    public String toString() {
      return text;
    }
  }

  private enum Transport {
    SOCKET("Socket"),
    SHMEM("Shared memory");

    private final String text;
    Transport(String text) {
      this.text = text;
    }

    @Override
    public String toString() {
      return text;
    }
  }

  private enum JDKVersionItem {
    JDK9(JavaSdkVersion.JDK_1_9) {
      @Override
      String getLaunchCommandLine(RemoteConnection connection) {
        String commandLine = JDK5to8.getLaunchCommandLine(connection);
        if (connection.isUseSockets() && !connection.isServerMode()) {
          commandLine = commandLine.replace(connection.getAddress(), "*:" + connection.getAddress());
        }
        return commandLine;
      }

      @Override
      public String toString() {
        return "JDK 9 or later";
      }
    },
    JDK5to8(JavaSdkVersion.JDK_1_5)  {
      @Override
      String getLaunchCommandLine(RemoteConnection connection) {
        return connection.getLaunchCommandLine().replace("-Xdebug", "").replace("-Xrunjdwp:", "-agentlib:jdwp=").trim();
      }

      @Override
      public String toString() {
        return "JDK 5 - 8";
      }
    },
    JDK1_4(JavaSdkVersion.JDK_1_4) {
      @Override
      String getLaunchCommandLine(RemoteConnection connection) {
        return connection.getLaunchCommandLine();
      }

      @Override
      public String toString() {
        return "JDK 1.4.x";
      }
    },
    JDK1_3(JavaSdkVersion.JDK_1_3) {
      @Override
      String getLaunchCommandLine(RemoteConnection connection) {
        return "-Xnoagent -Djava.compiler=NONE " + connection.getLaunchCommandLine();
      }

      @Override
      public String toString() {
        return "JDK 1.3.x or earlier";
      }
    };

    private final JavaSdkVersion myVersion;

    JDKVersionItem(JavaSdkVersion version) {
      myVersion = version;
    }

    abstract String getLaunchCommandLine(RemoteConnection connection);
  }

  private final JPanel          mainPanel;
  private final JTextArea       myArgsArea = new JTextArea();
  private final JComboBox<Mode> myModeCombo = new ComboBox<>(Mode.values());
  private final JComboBox<Transport> myTransportCombo = new ComboBox<>(Transport.values());

  private final ConfigurationModuleSelector myModuleSelector;

  private final JTextField myHostName = new JTextField();
  private final JTextField myAddress = new JTextField();
  private final IntegerField myPort = new IntegerField("&Port:", 0, 0xFFFF);

  public RemoteConfigurable(Project project) {
    myTransportCombo.setSelectedItem(Transport.SOCKET);

    myPort.setValue(myPort.getMaxValue());
    myPort.setMinimumSize(myPort.getPreferredSize());

    GridBagConstraints gc = new GridBagConstraints(0, 0, 2, 1, 0, 0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
                                                   JBUI.insets(4, 0, 0, 8), 0, 0);
    mainPanel = createModePanel(gc);

    JavaSdkVersion version = JavaSdkVersionUtil.getJavaSdkVersion(ProjectRootManager.getInstance(project).getProjectSdk());
    JDKVersionItem vi = version != null ?
                        Arrays.stream(JDKVersionItem.values()).filter(v -> version.isAtLeast(v.myVersion)).findFirst().orElse(JDKVersionItem.JDK9)
                                        : JDKVersionItem.JDK9;

    myArgsArea.setLineWrap(true);
    myArgsArea.setWrapStyleWord(true);
    myArgsArea.setRows(2);
    myArgsArea.setEditable(false);
    myArgsArea.setBorder(new SideBorder(JBColor.border(), SideBorder.ALL));
    myArgsArea.setMinimumSize(myArgsArea.getPreferredSize());
    myArgsArea.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myArgsArea.selectAll();
      }
    });

    updateArgsText(vi);

    DropDownLink<JDKVersionItem> ddl = new DropDownLink<>(vi, Arrays.asList(JDKVersionItem.values()), i -> updateArgsText(i), true);
    ddl.setToolTipText("JVM arguments format");

    gc.gridx = 0;
    gc.gridy++;
    gc.gridwidth = 5;
    gc.weightx = 1.0;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 0;
    gc.insets = JBUI.insetsTop(10);

    mainPanel.add(UI.PanelFactory.panel(myArgsArea).withLabel("&Command line arguments for remote JVM:").
      moveLabelOnTop().withTopRightComponent(ddl).
                               withComment("Copy and paste the arguments to the command line when JVM is started").createPanel(), gc);

    ModuleDescriptionsComboBox myModuleCombo = new ModuleDescriptionsComboBox();
    myModuleCombo.allowEmptySelection("<whole project>");
    myModuleSelector = new ConfigurationModuleSelector(project, myModuleCombo);

    gc.gridx = 0;
    gc.gridy++;
    gc.gridwidth = 5;
    gc.weightx = 1.0;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 0;
    gc.insets = JBUI.insetsTop(21);
    mainPanel.add(UI.PanelFactory.panel(myModuleCombo).withLabel("Use &module classpath:").
      withComment("First search for sources of the debugged classes in the selected module classpath").createPanel(), gc);

    gc.gridy++;
    gc.fill = GridBagConstraints.REMAINDER;
    gc.insets = JBUI.emptyInsets();
    gc.weighty = 1.0;
    mainPanel.add(new JPanel(), gc);

    DocumentListener textUpdateListener = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateArgsText(ddl.getChosenItem());
      }
    };

    myAddress.getDocument().addDocumentListener(textUpdateListener);
    myHostName.getDocument().addDocumentListener(textUpdateListener);
    myPort.getDocument().addDocumentListener(textUpdateListener);

    myModeCombo.addActionListener(l -> updateArgsText(ddl.getChosenItem()));
    myTransportCombo.addActionListener(l -> updateArgsText(ddl.getChosenItem()));
  }

  private void updateArgsText(@NotNull JDKVersionItem vi) {
    boolean useSockets = myTransportCombo.getSelectedItem() == Transport.SOCKET;

    RemoteConnection connection = new RemoteConnection(useSockets, myHostName.getText().trim(),
                                                       useSockets ? myPort.getText().trim() : myAddress.getText().trim(),
                                                       myModeCombo.getSelectedItem() == Mode.LISTEN
    );

    myArgsArea.setText(vi.getLaunchCommandLine(connection));
  }

  @Override
  protected void resetEditorFrom(@NotNull RemoteConfiguration rc) {
    myModeCombo.setSelectedItem(rc.SERVER_MODE ? Mode.LISTEN : Mode.ATTACH);

    if (SystemInfo.isWindows) {
      myTransportCombo.setSelectedItem(rc.USE_SOCKET_TRANSPORT ? Transport.SOCKET : Transport.SHMEM);
      if (!rc.USE_SOCKET_TRANSPORT) {
        myAddress.setText(rc.SHMEM_ADDRESS);
      }
    }

    if (!SystemInfo.isWindows || rc.USE_SOCKET_TRANSPORT) {
      rc.USE_SOCKET_TRANSPORT = true;

      myHostName.setText(rc.HOST);
      myPort.setValue(Integer.parseInt(rc.PORT));
    }

    myModuleSelector.reset(rc);
  }

  @Override
  protected void applyEditorTo(@NotNull RemoteConfiguration rc) throws ConfigurationException {
    rc.HOST = myHostName.getText().trim();
    if (rc.HOST.isEmpty()) {
      rc.HOST = null;
    }

    rc.PORT = myPort.getText().trim();
    if (rc.PORT.isEmpty()) {
      rc.PORT = null;
    }

    rc.SHMEM_ADDRESS = myAddress.getText().trim();
    if (rc.SHMEM_ADDRESS.isEmpty()) {
      rc.SHMEM_ADDRESS = null;
    }

    rc.USE_SOCKET_TRANSPORT = myTransportCombo.getSelectedItem() == Transport.SOCKET;
    if (rc.USE_SOCKET_TRANSPORT) {
      myPort.validateContent();
    }

    rc.SERVER_MODE = myModeCombo.getSelectedItem() == Mode.LISTEN;
    myModuleSelector.applyTo(rc);
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return mainPanel;
  }

  private static JLabel createLabelFor(String labelText, JComponent forComponent) {
    JLabel label = new JLabel();
    LabeledComponent.TextWithMnemonic.fromTextWithMnemonic(labelText).setToLabel(label);
    label.setLabelFor(forComponent);
    return label;
  }

  private JPanel createModePanel(GridBagConstraints gc) {
    JPanel panel = new JPanel(new GridBagLayout());

    JLabel modeLabel = createLabelFor("&Debugger mode:", myModeCombo);
    JLabel transportLabel = createLabelFor("&Transport:", myTransportCombo);
    JLabel hostLabel = createLabelFor("&Host:", myHostName);
    JLabel portLabel = createLabelFor(myPort.getValueName(), myPort);

    panel.add(modeLabel, gc);

    gc.gridx += 2;
    gc.gridwidth = 1;
    gc.insets = JBUI.insetsTop(4);
    panel.add(myModeCombo, gc);

    gc.gridx++;
    gc.weightx = 1.0;
    gc.gridwidth = 2;
    gc.fill = GridBagConstraints.REMAINDER;
    panel.add(new JPanel(), gc);

    if (SystemInfo.isWindows) {
      JLabel addressLabel = createLabelFor("&Address:", myAddress);

      addressLabel.setVisible(false);
      myAddress.setVisible(false);

      gc.gridx = 0;
      gc.gridy++;
      gc.weightx = 0.0;
      gc.gridwidth = 2;
      gc.fill = GridBagConstraints.NONE;
      gc.insets = JBUI.insets(4, 0, 0, 8);
      panel.add(transportLabel, gc);

      gc.gridx += 2;
      gc.gridwidth = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.insets = JBUI.insetsTop(4);
      panel.add(myTransportCombo, gc);

      gc.gridx++;
      gc.weightx = 1.0;
      gc.gridwidth = 2;
      gc.fill = GridBagConstraints.REMAINDER;
      panel.add(new JPanel(), gc);

      gc.gridy++;
      gc.gridx = 0;
      gc.weightx = 0.0;
      gc.gridwidth = 1;
      gc.insets = JBUI.insets(4, 0, 0, 8);
      gc.fill = GridBagConstraints.NONE;
      panel.add(addressLabel, gc);

      gc.gridx++;
      gc.gridwidth = 2;
      gc.insets = JBUI.insetsTop(4);
      gc.fill = GridBagConstraints.HORIZONTAL;
      panel.add(myAddress, gc);

      myTransportCombo.addActionListener(e -> {
        boolean isShmem = myTransportCombo.getSelectedItem() == Transport.SHMEM;

        hostLabel.setVisible(!isShmem);
        myPort.setVisible(!isShmem);
        myHostName.setVisible(!isShmem);
        portLabel.setVisible(!isShmem);

        addressLabel.setVisible(isShmem);
        myAddress.setVisible(isShmem);
      });
    }

    gc.gridy++;
    gc.gridx = 0;
    gc.weightx = 0.0;
    gc.gridwidth = 1;
    gc.insets = JBUI.insets(4, 0, 0, 8);
    gc.fill = GridBagConstraints.NONE;
    panel.add(hostLabel, gc);

    gc.gridx++;
    gc.gridwidth = 2;
    gc.insets = JBUI.insetsTop(4);
    gc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(myHostName, gc);

    gc.gridx += 2;
    gc.gridwidth = 1;
    gc.fill = GridBagConstraints.NONE;
    gc.insets = JBUI.insets(4, 20, 0, 8);
    panel.add(portLabel, gc);

    gc.gridx++;
    gc.insets = JBUI.insetsTop(4);
    panel.add(myPort, gc);

    return panel;
  }
}
