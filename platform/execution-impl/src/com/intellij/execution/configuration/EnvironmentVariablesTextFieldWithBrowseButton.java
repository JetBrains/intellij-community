// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configuration;

import com.google.common.collect.ImmutableMap;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.util.EnvVariablesTable;
import com.intellij.execution.util.EnvironmentVariable;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.UserActivityProviderComponent;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class EnvironmentVariablesTextFieldWithBrowseButton extends TextFieldWithBrowseButton implements UserActivityProviderComponent {

  protected EnvironmentVariablesData myData = EnvironmentVariablesData.DEFAULT;
  protected final Map<String, String> myParentDefaults = new LinkedHashMap<>();
  private final List<ChangeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public EnvironmentVariablesTextFieldWithBrowseButton() {
    super();
    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        setEnvs(EnvVariablesTable.parseEnvsFromText(getText()));
        createDialog().show();
      }
    });
    getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        if (!StringUtil.equals(stringifyEnvs(myData), getText())) {
          Map<String, String> textEnvs = EnvVariablesTable.parseEnvsFromText(getText());
          myData = myData.with(textEnvs);
          fireStateChanged();
        }
      }
    });
  }

  @NotNull
  protected EnvironmentVariablesDialog createDialog() {
    return new EnvironmentVariablesDialog(this);
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
    setData(myData.with(envs));
  }

  @NotNull
  public EnvironmentVariablesData getData() {
    return myData;
  }

  public void setData(@NotNull EnvironmentVariablesData data) {
    EnvironmentVariablesData oldData = myData;
    myData = data;
    setText(stringifyEnvs(data));
    if (!oldData.equals(data)) {
      fireStateChanged();
    }
  }

  @NotNull
  @Override
  protected Icon getDefaultIcon() {
    return AllIcons.General.InlineVariables;
  }

  @NotNull
  @Override
  protected Icon getHoveredIcon() {
    return AllIcons.General.InlineVariablesHover;
  }

  @NotNull
  protected String stringifyEnvs(@NotNull EnvironmentVariablesData evd) {
    if (evd.getEnvs().isEmpty()) {
      return "";
    }
    StringBuilder buf = new StringBuilder();
    for (Map.Entry<String, String> entry : evd.getEnvs().entrySet()) {
      if (buf.length() > 0) {
        buf.append(";");
      }
      buf.append(StringUtil.escapeChar(entry.getKey(), ';'))
        .append("=")
        .append(StringUtil.escapeChar(entry.getValue(), ';'));
    }
    return buf.toString();
  }

  public boolean isPassParentEnvs() {
    return myData.isPassParentEnvs();
  }

  public void setPassParentEnvs(boolean passParentEnvs) {
    setData(myData.with(passParentEnvs));
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

  protected static List<EnvironmentVariable> convertToVariables(Map<String, String> map, final boolean readOnly) {
    return ContainerUtil.map(map.entrySet(), entry -> new EnvironmentVariable(entry.getKey(), entry.getValue(), readOnly) {
      @Override
      public boolean getNameIsWriteable() {
        return !readOnly;
      }
    });
  }

  @Override
  protected @NotNull @NlsContexts.Tooltip String getIconTooltip() {
    return ExecutionBundle.message("specify.environment.variables.tooltip") + " (" +
           KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)) + ")";
  }

  protected boolean isModifiedSysEnv(@NotNull EnvironmentVariable v) {
    return !v.getNameIsWriteable() && !Objects.equals(v.getValue(), myParentDefaults.get(v.getName()));
  }
}