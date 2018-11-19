// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configuration;

import com.google.common.collect.ImmutableMap;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.EnvVariablesTable;
import com.intellij.execution.util.EnvironmentVariable;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.UserActivityProviderComponent;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class EnvironmentVariablesTextFieldWithBrowseButton extends TextFieldWithBrowseButton implements UserActivityProviderComponent {

  private EnvironmentVariablesData myData = EnvironmentVariablesData.DEFAULT;
  private final List<ChangeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public EnvironmentVariablesTextFieldWithBrowseButton() {
    super();
    setEditable(false);
    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        new MyEnvironmentVariablesDialog().show();
      }
    });
  }

  /**
   * @return unmodifiable Map instance
   */
  @NotNull
  public Map<String, String> getEnvs() {
    return myData.getEnvs();
  }

  /**
   * @param envs Map instance containing user-defined environment variables
   *             (iteration order should be reliable user-specified, like {@link LinkedHashMap} or {@link ImmutableMap})
   */
  public void setEnvs(@NotNull Map<String, String> envs) {
    setData(EnvironmentVariablesData.create(envs, myData.isPassParentEnvs()));
  }

  @NotNull
  public EnvironmentVariablesData getData() {
    return myData;
  }

  public void setData(@NotNull EnvironmentVariablesData data) {
    EnvironmentVariablesData oldData = myData;
    myData = data;
    setText(stringifyEnvs(data.getEnvs()));
    if (!oldData.equals(data)) {
      fireStateChanged();
    }
  }

  @NotNull
  private static String stringifyEnvs(@NotNull Map<String, String> envs) {
    if (envs.isEmpty()) {
      return "";
    }
    StringBuilder buf = new StringBuilder();
    for (Map.Entry<String, String> entry : envs.entrySet()) {
      if (buf.length() > 0) {
        buf.append(";");
      }
      buf.append(entry.getKey()).append("=").append(entry.getValue());
    }
    return buf.toString();
  }

  public boolean isPassParentEnvs() {
    return myData.isPassParentEnvs();
  }

  public void setPassParentEnvs(boolean passParentEnvs) {
    setData(EnvironmentVariablesData.create(myData.getEnvs(), passParentEnvs));
  }

  @Override
  public void addChangeListener(@NotNull ChangeListener changeListener) {
    myListeners.add(changeListener);
  }

  @Override
  public void removeChangeListener(@NotNull ChangeListener changeListener) {
    myListeners.remove(changeListener);
  }

  private void fireStateChanged() {
    for (ChangeListener listener : myListeners) {
      listener.stateChanged(new ChangeEvent(this));
    }
  }

  public static void showParentEnvironmentDialog(@NotNull Component parent) {
    EnvVariablesTable table = new EnvVariablesTable();
    table.setValues(convertToVariables(new TreeMap<>(new GeneralCommandLine().getParentEnvironment()), true));
    table.getActionsPanel().setVisible(false);
    DialogBuilder builder = new DialogBuilder(parent);
    builder.setTitle(ExecutionBundle.message("environment.variables.system.dialog.title"));
    builder.centerPanel(table.getComponent());
    builder.addCloseButton();
    builder.show();
  }

  private static List<EnvironmentVariable> convertToVariables(Map<String, String> map, final boolean readOnly) {
    return ContainerUtil.map(map.entrySet(), entry -> new EnvironmentVariable(entry.getKey(), entry.getValue(), readOnly) {
      @Override
      public boolean getNameIsWriteable() {
        return !readOnly;
      }
    });
  }

  private class MyEnvironmentVariablesDialog extends DialogWrapper {
    private final EnvVariablesTable myEnvVariablesTable;
    private final JCheckBox myUseDefaultCb = new JCheckBox(ExecutionBundle.message("env.vars.checkbox.title"));
    private final JPanel myWholePanel = new JPanel(new BorderLayout());

    protected MyEnvironmentVariablesDialog() {
      super(EnvironmentVariablesTextFieldWithBrowseButton.this, true);
      myEnvVariablesTable = new EnvVariablesTable();
      myEnvVariablesTable.setValues(convertToVariables(myData.getEnvs(), false));

      myUseDefaultCb.setSelected(isPassParentEnvs());
      myWholePanel.add(myEnvVariablesTable.getComponent(), BorderLayout.CENTER);
      JPanel useDefaultPanel = new JPanel(new BorderLayout());
      useDefaultPanel.add(myUseDefaultCb, BorderLayout.CENTER);
      HyperlinkLabel showLink = new HyperlinkLabel(ExecutionBundle.message("env.vars.show.system"));
      useDefaultPanel.add(showLink, BorderLayout.EAST);
      showLink.addHyperlinkListener(new HyperlinkListener() {
        @Override
        public void hyperlinkUpdate(HyperlinkEvent e) {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            showParentEnvironmentDialog(MyEnvironmentVariablesDialog.this.getWindow());
          }
        }
      });

      myWholePanel.add(useDefaultPanel, BorderLayout.SOUTH);
      setTitle(ExecutionBundle.message("environment.variables.dialog.title"));
      init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return myWholePanel;
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
      for (EnvironmentVariable variable : myEnvVariablesTable.getEnvironmentVariables()) {
        String name = variable.getName(), value = variable.getValue();
        if (!EnvironmentUtil.isValidName(name)) return new ValidationInfo(IdeBundle.message("run.configuration.invalid.env.name", name));
        if (!EnvironmentUtil.isValidValue(value)) return new ValidationInfo(IdeBundle.message("run.configuration.invalid.env.value", name, value));
      }
      return super.doValidate();
    }

    @Override
    protected void doOKAction() {
      myEnvVariablesTable.stopEditing();
      final Map<String, String> envs = new LinkedHashMap<>();
      for (EnvironmentVariable variable : myEnvVariablesTable.getEnvironmentVariables()) {
        envs.put(variable.getName(), variable.getValue());
      }
      setEnvs(envs);
      setPassParentEnvs(myUseDefaultCb.isSelected());
      super.doOKAction();
    }
  }
}