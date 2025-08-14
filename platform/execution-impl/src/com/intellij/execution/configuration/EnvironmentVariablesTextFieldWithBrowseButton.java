// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;

public class EnvironmentVariablesTextFieldWithBrowseButton extends TextFieldWithBrowseButton.NoPathCompletion implements UserActivityProviderComponent {
  protected EnvironmentVariablesData myData = EnvironmentVariablesData.DEFAULT;
  protected final Map<String, String> myParentDefaults = new LinkedHashMap<>();
  private final List<ChangeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private @NotNull List<String> myEnvFilePaths = new ArrayList<>();
  private ExtendableTextComponent.Extension myEnvFilesExtension;
  private final List<ChangeListener> myEnvFilePathsChangeListeners = ContainerUtil.createLockFreeCopyOnWriteList();

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
        if (!StringUtil.equals(getEnvText(), getText())) {
          Map<String, String> textEnvs = EnvVariablesTable.parseEnvsFromText(getText());
          myData = myData.with(textEnvs);
          updateEnvFilesFromText();
          fireStateChanged();
        }
      }
    });
    getTextField().getEmptyText().setText(ExecutionBundle.message("status.text.environment.variables"));
  }

  private void addEnvFilesExtension() {
    if (myEnvFilesExtension != null) return;
    myEnvFilesExtension = ExtendableTextComponent.Extension.create(AllIcons.General.OpenDisk, AllIcons.General.OpenDiskHover,
                                             ExecutionBundle.message("tooltip.browse.for.environment.files"), () -> browseForEnvFile());
    getTextField().addExtension(myEnvFilesExtension);
    getTextField().getEmptyText().setText(ExecutionBundle.message("status.text.environment.variables.or.env.files"));
  }

  private void browseForEnvFile() {
    if (myEnvFilePaths.isEmpty()) {
      EnvFilesDialogKt.addEnvFile(getTextField(), null, s -> {
        myEnvFilePaths.add(s);
        updateText();
        fireEnvFilePathsChanged();
        return null;
      });
    }
    else {
      EnvFilesDialog dialog = new EnvFilesDialog(this, myEnvFilePaths);
      dialog.show();
      if (dialog.isOK()) {
        myEnvFilePaths = new ArrayList<>(dialog.getPaths());
        updateText();
        fireEnvFilePathsChanged();
      }
    }
  }

  @Override
  public @NotNull ExtendableTextField getTextField() {
    return (ExtendableTextField)super.getTextField();
  }

  protected @NotNull EnvironmentVariablesDialog createDialog() {
    return new EnvironmentVariablesDialog(this);
  }

  /**
   * @return unmodifiable Map instance
   */
  public @NotNull Map<String, String> getEnvs() {
    return myData.getEnvs();
  }

  /**
   * @param envs Map instance containing user-defined environment variables
   *             (iteration order should be reliable user-specified, like {@link LinkedHashMap} or {@link ImmutableMap})
   */
  public void setEnvs(@NotNull Map<String, String> envs) {
    setData(myData.with(envs));
  }

  public @NotNull EnvironmentVariablesData getData() {
    return myData;
  }

  public void setData(@NotNull EnvironmentVariablesData data) {
    EnvironmentVariablesData oldData = myData;
    myData = data;
    updateText();
    if (!oldData.equals(data)) {
      fireStateChanged();
    }
  }

  @Override
  protected @NotNull Icon getDefaultIcon() {
    return AllIcons.General.InlineVariables;
  }

  @Override
  protected @NotNull Icon getHoveredIcon() {
    return AllIcons.General.InlineVariablesHover;
  }

  private String getEnvText() {
    String s = stringifyEnvs(myData);
    if (myEnvFilePaths.isEmpty()) return s;
    StringBuilder buf = new StringBuilder(s);
    for (String path : myEnvFilePaths) {
      if (!buf.isEmpty()) {
        buf.append(";");
      }
      buf.append(path);
    }
    return buf.toString();
  }

  private void updateText() {
    setText(getEnvText());
  }

  protected @NotNull String stringifyEnvs(@NotNull EnvironmentVariablesData evd) {
    StringBuilder buf = new StringBuilder();
    for (Map.Entry<String, String> entry : evd.getEnvs().entrySet()) {
      if (!buf.isEmpty()) {
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

  public void addEnvFilePathsChangeListener(@NotNull ChangeListener changeListener) {
    myEnvFilePathsChangeListeners.add(changeListener);
  }

  public void removeEnvFilePathsChangeListener(@NotNull ChangeListener changeListener) {
    myEnvFilePathsChangeListeners.remove(changeListener);
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

  private void fireEnvFilePathsChanged() {
    for (ChangeListener listener : myEnvFilePathsChangeListeners) {
      listener.stateChanged(new ChangeEvent(this));
    }
  }

  void setEnvFilePaths(@NotNull List<String> paths) {
    myEnvFilePaths = new ArrayList<>(paths);
    setData(myData);
    addEnvFilesExtension();
    fireEnvFilePathsChanged();
  }

  @NotNull List<String> getEnvFilePaths() {
    return myEnvFilePaths;
  }

  private void updateEnvFilesFromText() {
    String text = StringUtil.trimStart(getText(), stringifyEnvs(myData));
    if (myEnvFilePaths.isEmpty() || text.isEmpty()) return;
    List<String> paths = ContainerUtil.filter(ContainerUtil.map(text.split(";"), s -> s.trim()), s -> !s.isEmpty());
    for (int i = 0; i < Math.min(myEnvFilePaths.size(), paths.size()); i++) {
      myEnvFilePaths.set(i, paths.get(i));
    }
    fireEnvFilePathsChanged();
  }

  protected static @Unmodifiable List<EnvironmentVariable> convertToVariables(Map<String, String> map, final boolean readOnly) {
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
