// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.execution.ShortenCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Computable;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionListener;

public class ShortenCommandLineModeCombo extends ComboBox<ShortenCommandLine> {
  private final Project myProject;

  public ShortenCommandLineModeCombo(Project project,
                                     JrePathEditor pathEditor,
                                     ModuleDescriptionsComboBox component) {
    this(project, pathEditor, component::getSelectedModule, component::addActionListener);
  }

  public ShortenCommandLineModeCombo(Project project,
                                     JrePathEditor pathEditor,
                                     Computable<? extends Module> component,
                                     Consumer<? super ActionListener> listenerConsumer) {
    myProject = project;
    initModel(null, pathEditor, component.compute());
    setRenderer(new ColoredListCellRenderer<>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends ShortenCommandLine> list,
                                           ShortenCommandLine value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        if (value == null) {
          ShortenCommandLine defaultMode = ShortenCommandLine.getDefaultMethod(myProject, getJdkRoot(pathEditor, component.compute()));
          append(JavaBundle.message("user.local.default.shorten.command.line.option", defaultMode.getPresentableName()))
            .append(" - " + defaultMode.getDescription(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        else {
          append(value.getPresentableName()).append(" - " + value.getDescription(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
      }
    });
    ActionListener updateModelListener = e -> {
      ShortenCommandLine item = getSelectedItem();
      initModel(item, pathEditor, component.compute());
    };
    pathEditor.addActionListener(updateModelListener);
    listenerConsumer.consume(updateModelListener);
  }

  private void initModel(ShortenCommandLine preselection, JrePathEditor pathEditor, Module module) {
    removeAllItems();

    String jdkRoot = getJdkRoot(pathEditor, module);
    addItem(null);
    for (ShortenCommandLine mode : ShortenCommandLine.values()) {
      if (mode.isApplicable(jdkRoot)) {
        addItem(mode);
      }
    }

    setSelectedItem(preselection);
  }

  @Nullable
  private String getJdkRoot(JrePathEditor pathEditor, Module module) {
    if (!pathEditor.isAlternativeJreSelected() && module != null) {
      Sdk sdk = JavaParameters.getJdkToRunModule(module, productionOnly());
      return sdk != null ? sdk.getHomePath() : null;
    }
    String jrePathOrName = pathEditor.getJrePathOrName();
    if (jrePathOrName != null) {
      Sdk configuredJdk = ProjectJdkTable.getInstance().findJdk(jrePathOrName);
      if (configuredJdk != null) {
        return configuredJdk.getHomePath();
      }
      else {
        return jrePathOrName;
      }
    }
    return null;
  }

  protected boolean productionOnly() {
    return true;
  }

  @Nullable
  @Override
  public ShortenCommandLine getSelectedItem() {
    return (ShortenCommandLine)super.getSelectedItem();
  }
}
