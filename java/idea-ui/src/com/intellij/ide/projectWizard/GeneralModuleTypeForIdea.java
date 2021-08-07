// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.GeneralModuleType;
import org.jetbrains.annotations.NotNull;

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
        return new ProjectSettingsStep(context);
      }
    };
  }
}
