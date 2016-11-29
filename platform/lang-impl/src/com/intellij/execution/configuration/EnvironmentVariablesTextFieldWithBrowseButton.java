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
package com.intellij.execution.configuration;

import com.google.common.collect.ImmutableMap;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.EnvVariablesTable;
import com.intellij.execution.util.EnvironmentVariable;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.UserActivityProviderComponent;
import com.intellij.util.Function;
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
import java.util.*;
import java.util.List;

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
    if (oldData.isPassParentEnvs() != data.isPassParentEnvs()) {
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
  public void addChangeListener(ChangeListener changeListener) {
    myListeners.add(changeListener);
  }

  @Override
  public void removeChangeListener(ChangeListener changeListener) {
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

    @Override
    @Nullable
    protected JComponent createCenterPanel() {
      return myWholePanel;
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
