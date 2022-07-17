// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.execution.ShortenCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ShortenCommandLineModeCombo extends ComboBox<ShortenCommandLine> {
  private final Supplier<ShortenCommandLine> myDefaultMethodSupplier;

  public ShortenCommandLineModeCombo(Project project,
                                     JrePathEditor pathEditor,
                                     ModuleDescriptionsComboBox component) {
    this(project, pathEditor, component::getSelectedModule, component::addActionListener);
  }

  public ShortenCommandLineModeCombo(Project project,
                                     JrePathEditor pathEditor,
                                     Supplier<? extends Module> component,
                                     Consumer<? super ActionListener> listenerConsumer) {
    myDefaultMethodSupplier = () -> ShortenCommandLine.getDefaultMethod(project, getJdkRoot(pathEditor, component.get()));
    initModel(myDefaultMethodSupplier.get(), pathEditor, component.get());
    setRenderer(new ColoredListCellRenderer<>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends ShortenCommandLine> list,
                                           ShortenCommandLine value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        append(value.getPresentableName()).append(" - " + value.getDescription(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    });
    ActionListener updateModelListener = e -> {
      ShortenCommandLine item = getSelectedItem();
      initModel(item, pathEditor, component.get());
    };
    pathEditor.addActionListener(updateModelListener);
    listenerConsumer.accept(updateModelListener);
  }

  private void initModel(ShortenCommandLine preselection, JrePathEditor pathEditor, Module module) {
    removeAllItems();

    String jdkRoot = getJdkRoot(pathEditor, module);
    for (ShortenCommandLine mode : ShortenCommandLine.values()) {
      if (mode.isApplicable(jdkRoot)) {
        addItem(mode);
      }
    }

    setSelectedItem(preselection);
  }

  @Nullable
  private String getJdkRoot(JrePathEditor pathEditor, Module module) {
    if (!pathEditor.isAlternativeJreSelected()) {
      if (module != null) {
        Sdk sdk = JavaParameters.getJdkToRunModule(module, productionOnly());
        return sdk != null ? sdk.getHomePath() : null;
      }
      return null;
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

  @Override
  public void setSelectedItem(Object anObject) {
    super.setSelectedItem(ObjectUtils.notNull(anObject, myDefaultMethodSupplier.get()));
  }
}
