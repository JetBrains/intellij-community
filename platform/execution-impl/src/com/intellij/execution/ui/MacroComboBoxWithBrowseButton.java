// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.BrowseFolderRunnable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.ui.TextAccessor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class MacroComboBoxWithBrowseButton extends ComboBox<String> implements TextAccessor {
  private final Module myModule;

  public MacroComboBoxWithBrowseButton(FileChooserDescriptor descriptor, Project project) {
    super(new MacroComboBoxModel());

    var module = descriptor.getUserData(LangDataKeys.MODULE_CONTEXT);
    if (module == null) module = descriptor.getUserData(PlatformCoreDataKeys.MODULE);
    myModule = module;

    if (project == null && module != null) {
      project = module.getProject();
    }

    descriptor = descriptor.withShowHiddenFiles(true);

    var action = new BrowseFolderRunnable<ComboBox<String>>(project, descriptor, this, accessor) {
      @Override
      protected @NotNull String expandPath(@NotNull String path) {
        var project = getProject();
        if (project != null) path = PathMacroManager.getInstance(project).expandPath(path);
        if (myModule != null) path = PathMacroManager.getInstance(myModule).expandPath(path);
        return super.expandPath(path);
      }
    };

    initBrowsableEditor(action, project);

    Component component = editor.getEditorComponent();
    if (component instanceof JTextField) {
      FileChooserFactory.getInstance().installFileCompletion((JTextField)component, descriptor, true, null);
    }
  }

  @Override
  public String getText() {
    return accessor.getText(this);
  }

  @Override
  public void setText(String text) {
    accessor.setText(this, text != null ? text : "");
  }

  private final TextComponentAccessor<ComboBox<String>> accessor = new TextComponentAccessor<>() {
    @Override
    public String getText(ComboBox<String> component) {
      Object item = component == null ? null : component.getSelectedItem();
      return item == null ? "" : item.toString();
    }

    @Override
    public void setText(ComboBox<String> component, @NotNull String text) {
      if (component != null) component.setSelectedItem(text);
    }
  };
}
