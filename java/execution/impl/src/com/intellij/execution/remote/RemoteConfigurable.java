// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.remote;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.DropDownLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBInsets;
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
    ATTACH, LISTEN;

    @Override
    public String toString() {
      return this == ATTACH
             ? ExecutionBundle.message("combo.attach.to.remote")
             : ExecutionBundle.message("combo.listen.to.remote");
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
          commandLine = commandLine.replace(connection.getApplicationAddress(), "*:" + connection.getApplicationAddress());
        }
        return commandLine;
      }

      @Override
      public String toString() {
        return ExecutionBundle.message("combo.java.version.9+");
      }
    },
    JDK5to8(JavaSdkVersion.JDK_1_5)  {
      @Override
      String getLaunchCommandLine(RemoteConnection connection) {
        return connection.getLaunchCommandLine().replace("-Xdebug", "").replace("-Xrunjdwp:", "-agentlib:jdwp=").trim();
      }

      @Override
      public String toString() {
        return ExecutionBundle.message("combo.java.version.5.to.8");
      }
    },
    JDK1_4(JavaSdkVersion.JDK_1_4) {
      @Override
      String getLaunchCommandLine(RemoteConnection connection) {
        return connection.getLaunchCommandLine();
      }

      @Override
      public String toString() {
        return ExecutionBundle.message("combo.java.version.1.4");
      }
    },
    JDK1_3(JavaSdkVersion.JDK_1_3) {
      @Override
      String getLaunchCommandLine(RemoteConnection connection) {
        return "-Xnoagent -Djava.compiler=NONE " + connection.getLaunchCommandLine();
      }

      @Override
      public String toString() {
        return ExecutionBundle.message("combo.java.version.1.3");
      }
    };

    private final JavaSdkVersion myVersion;

    JDKVersionItem(JavaSdkVersion version) {
      myVersion = version;
    }

    abstract String getLaunchCommandLine(RemoteConnection connection);
  }

  private static final int MIN_PORT_VALUE = 0;
  private static final int MAX_PORT_VALUE = 0xFFFF;

  private final JPanel          mainPanel;
  private final JTextArea       myArgsArea = new JTextArea();
  private final JComboBox<Mode> myModeCombo = new ComboBox<>(Mode.values());
  private final JBCheckBox      myAutoRestart = new JBCheckBox(ExecutionBundle.message("auto.restart"));
  private final JComboBox<Transport> myTransportCombo = new ComboBox<>(Transport.values());

  private final ConfigurationModuleSelector myModuleSelector;

  private final JTextField myHostName = new JTextField();
  private final JTextField myAddress = new JTextField();
  private final JTextField myPort = new JTextField(Integer.toString(MAX_PORT_VALUE));

  public RemoteConfigurable(Project project) {
    myTransportCombo.setSelectedItem(Transport.SOCKET);

    myPort.setMinimumSize(myPort.getPreferredSize());
    new ComponentValidator(project).withValidator(() -> {
      String pt = myPort.getText();
      if (StringUtil.isNotEmpty(pt)) {
        try {
          int portValue = Integer.parseInt(pt);
          if (portValue >= MIN_PORT_VALUE && portValue <= MAX_PORT_VALUE) {
            return null;
          }
          else {
            return new ValidationInfo(ExecutionBundle.message("incorrect.port.range.set.value.between"), myPort);
          }
        }
        catch (NumberFormatException nfe) {
          return new ValidationInfo(ExecutionBundle.message("port.value.should.be.a.number.between"), myPort);
        }
      }
      else {
        return null;
      }
    }).installOn(myPort);

    myPort.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        ComponentValidator.getInstance(myPort).ifPresent(v -> v.revalidate());
      }
    });

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
    ddl.setToolTipText(ExecutionBundle.message("jvm.arguments.format"));

    gc.gridx = 0;
    gc.gridy++;
    gc.gridwidth = 6;
    gc.weightx = 1.0;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.insets = JBUI.insetsTop(10);

    mainPanel.add(UI.PanelFactory.panel(myArgsArea).withLabel(ExecutionBundle.message("command.line.arguments.for.remote.jvm")).
      moveLabelOnTop().withTopRightComponent(ddl).
                               withComment(ExecutionBundle.message("copy.and.paste.the.arguments.to.the.command.line.when.jvm.is.started")).createPanel(), gc);

    ModuleDescriptionsComboBox myModuleCombo = new ModuleDescriptionsComboBox();
    myModuleCombo.allowEmptySelection(JavaCompilerBundle.message("whole.project"));
    myModuleSelector = new ConfigurationModuleSelector(project, myModuleCombo);

    gc.gridx = 0;
    gc.gridy++;
    gc.gridwidth = 6;
    gc.weightx = 1.0;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.insets = JBUI.insetsTop(21);
    mainPanel.add(UI.PanelFactory.panel(myModuleCombo).withLabel(ExecutionBundle.message("use.module.classpath")).
      withComment(ExecutionBundle.message("first.search.for.sources.of.the.debugged.classes")).createPanel(), gc);

    gc.gridy++;
    gc.fill = GridBagConstraints.REMAINDER;
    gc.insets = JBInsets.emptyInsets();
    gc.weighty = 1.0;
    mainPanel.add(new JPanel(), gc);

    DocumentListener textUpdateListener = new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        updateArgsText(ddl.getSelectedItem());
      }
    };

    myAddress.getDocument().addDocumentListener(textUpdateListener);
    myHostName.getDocument().addDocumentListener(textUpdateListener);
    myPort.getDocument().addDocumentListener(textUpdateListener);

    myModeCombo.addActionListener(l -> updateArgsText(ddl.getSelectedItem()));
    myTransportCombo.addActionListener(l -> updateArgsText(ddl.getSelectedItem()));
  }

  private void updateArgsText(@NotNull JDKVersionItem vi) {
    myAutoRestart.setVisible(myModeCombo.getSelectedItem() == Mode.LISTEN);
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
    myAutoRestart.setSelected(rc.AUTO_RESTART);

    if (SystemInfo.isWindows) {
      myTransportCombo.setSelectedItem(rc.USE_SOCKET_TRANSPORT ? Transport.SOCKET : Transport.SHMEM);
      if (!rc.USE_SOCKET_TRANSPORT) {
        myAddress.setText(rc.SHMEM_ADDRESS);
      }
    }

    if (!SystemInfo.isWindows || rc.USE_SOCKET_TRANSPORT) {
      rc.USE_SOCKET_TRANSPORT = true;

      myHostName.setText(rc.HOST);
      myPort.setText(rc.PORT);
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
      ComponentValidator.getInstance(myPort).ifPresent(v -> v.revalidate());
    }

    rc.SERVER_MODE = myModeCombo.getSelectedItem() == Mode.LISTEN;
    rc.AUTO_RESTART = myAutoRestart.isVisible() && myAutoRestart.isSelected();
    myModuleSelector.applyTo(rc);
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return mainPanel;
  }

  private static JLabel createLabelFor(@NlsContexts.Label String labelText, JComponent forComponent) {
    JLabel label = new JLabel();
    LabeledComponent.TextWithMnemonic.fromTextWithMnemonic(labelText).setToLabel(label);
    label.setLabelFor(forComponent);
    return label;
  }

  private JPanel createModePanel(GridBagConstraints gc) {
    JPanel panel = new JPanel(new GridBagLayout());

    JLabel modeLabel = createLabelFor(JavaCompilerBundle.message("label.debugger.mode"), myModeCombo);
    JLabel transportLabel = createLabelFor(JavaCompilerBundle.message("label.transport"), myTransportCombo);
    JLabel hostLabel = createLabelFor(JavaCompilerBundle.message("label.host"), myHostName);
    JLabel portLabel = createLabelFor(JavaCompilerBundle.message("label.port"), myPort);

    gc.gridwidth = 2;
    panel.add(modeLabel, gc);

    gc.gridx += 2;
    gc.gridwidth = 1;
    gc.insets = JBUI.insetsTop(4);
    panel.add(myModeCombo, gc);

    gc.gridx++;
    gc.gridwidth = 2;
    gc.fill = GridBagConstraints.NONE;
    gc.insets = JBUI.insets(4, 20, 0, 8);
    panel.add(myAutoRestart, gc);

    gc.gridx += 2;
    gc.gridwidth = 1;
    gc.weightx = 1.0;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.insets = JBInsets.emptyInsets();
    panel.add(new JPanel(), gc);

    if (SystemInfo.isWindows) {
      JLabel addressLabel = createLabelFor(JavaCompilerBundle.message("label.address"), myAddress);

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

    gc.gridx++;
    gc.weightx = 1.0;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.insets = JBInsets.emptyInsets();
    panel.add(new JPanel(), gc);

    return panel;
  }
}
