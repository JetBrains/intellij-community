// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.GeneralModuleType;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class GeneralModuleTypeForIdea extends GeneralModuleType {
  @Override
  public @NotNull ModuleBuilder createModuleBuilder() {
    return new GeneralModuleBuilder() {
      @Override
      public @NotNull List<Class<? extends ModuleWizardStep>> getIgnoredSteps() {
        return List.of(ProjectSettingsStep.class);
      }

      @Override
      public @NotNull ModuleWizardStep getCustomOptionsStep(WizardContext context,
                                                            Disposable parentDisposable) {
        ProjectSettingsStep step = new ProjectSettingsStep(context);
        step.getExpertPlaceholder().removeAll();
        JTextPane textPane = new JTextPane();
        textPane.setText(getDescription());
        step.getExpertPlaceholder().setMinimumSize(new Dimension(0, 100));
        step.getExpertPlaceholder().add(ScrollPaneFactory.createScrollPane(textPane));
        return step;
      }
    };
  }
}
