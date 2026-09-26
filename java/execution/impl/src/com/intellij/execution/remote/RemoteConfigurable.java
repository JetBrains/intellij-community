// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.remote;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.remote.RemoteConfigurableUi.JdkVersionItem;
import com.intellij.execution.remote.RemoteConfigurableUi.Mode;
import com.intellij.execution.remote.RemoteConfigurableUi.Transport;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.java.JavaPluginDisposable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ItemEvent;
import java.util.Arrays;

public class RemoteConfigurable extends SettingsEditor<RemoteConfiguration> {

  private static final int MIN_PORT_VALUE = 0;
  private static final int MAX_PORT_VALUE = 0xFFFF;

  private final RemoteConfigurableUi ui = new RemoteConfigurableUi();

  private final ConfigurationModuleSelector myModuleSelector;

  public RemoteConfigurable(Project project) {
    new ComponentValidator(JavaPluginDisposable.getInstance(project)).withValidator(() -> {
      String pt = ui.port.getText();
      if (StringUtil.isNotEmpty(pt)) {
        try {
          int portValue = Integer.parseInt(pt);
          if (portValue >= MIN_PORT_VALUE && portValue <= MAX_PORT_VALUE) {
            return null;
          }
          else {
            return new ValidationInfo(ExecutionBundle.message("incorrect.port.range.set.value.between"), ui.port);
          }
        }
        catch (NumberFormatException nfe) {
          return new ValidationInfo(ExecutionBundle.message("port.value.should.be.a.number.between"), ui.port);
        }
      }
      else {
        return null;
      }
    }).installOn(ui.port);

    ui.port.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        ComponentValidator.getInstance(ui.port).ifPresent(v -> v.revalidate());
      }
    });

    JavaSdkVersion version = JavaSdkVersionUtil.getJavaSdkVersion(ProjectRootManager.getInstance(project).getProjectSdk());
    JdkVersionItem vi = version != null ?
                        Arrays.stream(JdkVersionItem.values()).filter(v -> version.isAtLeast(v.getVersion())).findFirst()
                          .orElse(JdkVersionItem.JDK9)
                                        : JdkVersionItem.JDK9;

    ui.jdkVersionLink.setSelectedItem(vi);
    ui.jdkVersionLink.addItemListener(e -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        updateArgsText(ui.jdkVersionLink.getSelectedItem());
      }
    });

    updateArgsText(vi);

    myModuleSelector = new ConfigurationModuleSelector(project, ui.moduleCombo);

    DocumentListener textUpdateListener = new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        updateArgsText(ui.jdkVersionLink.getSelectedItem());
      }
    };

    ui.address.getDocument().addDocumentListener(textUpdateListener);
    ui.hostName.getDocument().addDocumentListener(textUpdateListener);
    ui.port.getDocument().addDocumentListener(textUpdateListener);

    ui.modeCombo.addActionListener(l -> updateArgsText(ui.jdkVersionLink.getSelectedItem()));
    ui.transportCombo.addActionListener(l -> updateArgsText(ui.jdkVersionLink.getSelectedItem()));
  }

  private void updateArgsText(@NotNull JdkVersionItem vi) {
    boolean useSockets = ui.transportCombo.getSelectedItem() == Transport.SOCKET;

    RemoteConnection connection = new RemoteConnection(useSockets, ui.hostName.getText().trim(),
                                                       useSockets ? ui.port.getText().trim() : ui.address.getText().trim(),
                                                       ui.modeCombo.getSelectedItem() == Mode.LISTEN
    );

    ui.argsArea.setText(vi.getLaunchCommandLine(connection));
  }

  @Override
  protected void resetEditorFrom(@NotNull RemoteConfiguration rc) {
    ui.modeCombo.setSelectedItem(rc.SERVER_MODE ? Mode.LISTEN : Mode.ATTACH);
    ui.autoRestart.setSelected(rc.AUTO_RESTART);

    if (SystemInfo.isWindows) {
      ui.transportCombo.setSelectedItem(rc.USE_SOCKET_TRANSPORT ? Transport.SOCKET : Transport.SHMEM);
      if (!rc.USE_SOCKET_TRANSPORT) {
        ui.address.setText(rc.SHMEM_ADDRESS);
      }
    }

    if (!SystemInfo.isWindows || rc.USE_SOCKET_TRANSPORT) {
      rc.USE_SOCKET_TRANSPORT = true;

      ui.hostName.setText(rc.HOST);
      ui.port.setText(rc.PORT);
    }

    myModuleSelector.reset(rc);
  }

  @Override
  protected void applyEditorTo(@NotNull RemoteConfiguration rc) throws ConfigurationException {
    rc.HOST = ui.hostName.getText().trim();
    if (rc.HOST.isEmpty()) {
      rc.HOST = null;
    }

    rc.PORT = ui.port.getText().trim();
    if (rc.PORT.isEmpty()) {
      rc.PORT = null;
    }

    rc.SHMEM_ADDRESS = ui.address.getText().trim();
    if (rc.SHMEM_ADDRESS.isEmpty()) {
      rc.SHMEM_ADDRESS = null;
    }

    rc.USE_SOCKET_TRANSPORT = ui.transportCombo.getSelectedItem() == Transport.SOCKET;
    if (rc.USE_SOCKET_TRANSPORT) {
      ComponentValidator.getInstance(ui.port).ifPresent(v -> v.revalidate());
    }

    rc.SERVER_MODE = ui.modeCombo.getSelectedItem() == Mode.LISTEN;
    rc.AUTO_RESTART = rc.SERVER_MODE && ui.autoRestart.isSelected();
    myModuleSelector.applyTo(rc);
  }

  @Override
  protected @NotNull JComponent createEditor() {
    return ui.panel;
  }
}
