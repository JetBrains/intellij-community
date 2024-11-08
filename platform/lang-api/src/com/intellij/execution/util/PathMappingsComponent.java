// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.util;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.UserActivityProviderComponent;
import com.intellij.ui.dsl.builder.DslComponentProperty;
import com.intellij.ui.dsl.builder.VerticalComponentGap;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public final class PathMappingsComponent extends LabeledComponent<TextFieldWithBrowseButton> implements UserActivityProviderComponent {

  private final List<ChangeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private @NotNull PathMappingSettings myMappingSettings = new PathMappingSettings();

  public PathMappingsComponent() {
    super();
    final TextFieldWithBrowseButton pathTextField = new TextFieldWithBrowseButton();
    pathTextField.setEditable(false);
    setComponent(pathTextField);
    setText(ExecutionBundle.message("label.path.mappings"));
    putClientProperty(DslComponentProperty.INTERACTIVE_COMPONENT, pathTextField.getChildComponent());
    putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap.BOTH);
    getComponent().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        showConfigureMappingsDialog();
      }
    });
  }

  private void showConfigureMappingsDialog() {
    new MyPathMappingsDialog(this).show();
  }

  public @NotNull PathMappingSettings getMappingSettings() {
    return myMappingSettings;
  }

  public void setMappingSettings(final @Nullable PathMappingSettings mappingSettings) {
    if (mappingSettings == null) {
      myMappingSettings = new PathMappingSettings();
    }
    else {
      myMappingSettings = mappingSettings;
    }

    setTextRepresentation(myMappingSettings);

    fireStateChanged();
  }

  private void setTextRepresentation(@NotNull PathMappingSettings mappingSettings) {
    @NlsSafe StringBuilder sb = new StringBuilder();
    for (PathMappingSettings.PathMapping mapping : mappingSettings.getPathMappings()) {
      sb.append(mapping.getLocalRoot()).append("=").append(mapping.getRemoteRoot()).append(";");
    }
    if (sb.length() > 0) {
      sb.deleteCharAt(sb.length() - 1); //trim last ;
    }
    getComponent().setText(sb.toString());
  }

  @Override
  public void addChangeListener(final @NotNull ChangeListener changeListener) {
    myListeners.add(changeListener);
  }

  @Override
  public void removeChangeListener(final @NotNull ChangeListener changeListener) {
    myListeners.remove(changeListener);
  }

  private void fireStateChanged() {
    for (ChangeListener listener : myListeners) {
      listener.stateChanged(new ChangeEvent(this));
    }
  }

  private static class MyPathMappingsDialog extends DialogWrapper {
    private final PathMappingTable myPathMappingTable;

    private final JPanel myWholePanel = new JPanel(new BorderLayout());
    private final PathMappingsComponent myMappingsComponent;

    protected MyPathMappingsDialog(PathMappingsComponent mappingsComponent) {
      super(mappingsComponent, true);
      myMappingsComponent = mappingsComponent;
      myPathMappingTable = new PathMappingTable();

      myPathMappingTable.setValues(mappingsComponent.getMappingSettings().getPathMappings());
      myWholePanel.add(myPathMappingTable.getComponent(), BorderLayout.CENTER);
      setTitle(ExecutionBundle.message("dialog.title.edit.path.mappings"));
      init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
      return myWholePanel;
    }

    @Override
    protected void doOKAction() {
      myPathMappingTable.stopEditing();

      myMappingsComponent.setMappingSettings(myPathMappingTable.getPathMappingSettings());

      super.doOKAction();
    }
  }
}
