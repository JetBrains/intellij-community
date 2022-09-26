// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import org.jetbrains.annotations.NotNull;

import java.util.List;

@Deprecated
public interface CustomizeIDEWizardStepsProvider {
  /**
   * Called for the initial IDE customization wizard, which is shown before the splash screen.
   * The provided steps run before an {@link com.intellij.openapi.application.Application} instance is created,
   * and thus can change the list of enabled plugins.
   * If no steps are provided, the dialog is not shown, and loading proceeds to the splash screen.
   */
  void initSteps(CustomizeIDEWizardDialog wizardDialog, @NotNull List<? super AbstractCustomizeWizardStep> steps);

  /**
   * Called for an optional secondary customization dialog shown after the splash screen, right before the welcome screen.
   * The provided steps run after the {@link com.intellij.openapi.application.Application} is created and initialized.
   * If no steps are provided, the secondary dialog is not shown, and loading proceeds to the welcome screen.
   */
  default void initStepsAfterSplash(@NotNull CustomizeIDEWizardDialog wizardDialog,
                                    @NotNull List<AbstractCustomizeWizardStep> steps) {}

  default boolean hideSkipButton() {
    return false;
  }
}