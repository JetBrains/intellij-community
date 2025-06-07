// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Defines contract for a component which knows how to manage particular settings.
 * 
 * @param <S>  settings type
 */
public interface ExternalSystemSettingsControl<S> {

  /**
   * Adds current control-specific UI controls to the given canvas.
   * <p/>
   * <b>Note:</b> given canvas component is expected to be managed by a {@link GridBagLayout}. That is the reason on why we use
   * this method instead of a method like 'JComponent getComponent()' - there is a possible case that given canvas has components
   * from more than one control and we might want them to be aligned.
   * 
   * @param canvas        container to use as a holder for UI components specific to the current control
   * @param indentLevel   a hint on how much UI components added by the current control should be indented
   */
  void fillUi(@NotNull PaintAwarePanel canvas, int indentLevel);

  /**
   * Asks current control to reset its state to the initial one.
   */
  void reset();

  /**
   * Asks current control to reset its state to the initial one.
   */
  default void reset(@Nullable Project project) {
    reset();
  }

  /**
   * Asks current control to reset its state to the initial one.
   */
  default void reset(@Nullable WizardContext wizardContext) {
    reset();
  }

  /**
   * @return    {@code true} if settings exposed by the current control have been modified; {@code false} otherwise
   */
  boolean isModified();

  /**
   * Asks current control to fill given settings with the current user-defined values.
   * 
   * @param settings  settings holder
   */
  void apply(@NotNull S settings);

  /**
   * Asks current control to validate given settings with the current user-defined values.
   *
   * @param settings  settings holder
   */
  boolean validate(@NotNull S settings) throws ConfigurationException;

  void disposeUIResources();

  /**
   * Hides/shows {@link #fillUi(PaintAwarePanel, int) components added by the current control}.
   * 
   * @param show  flag which indicates if current control' components should be visible
   */
  void showUi(boolean show);

  default @Nullable String getHelpId() {
    return null;
  }
}
