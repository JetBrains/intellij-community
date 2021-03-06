// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * This interface represents a configurable component that provides a Swing form to configure some settings.
 * Sometimes the IDE instantiates it on a background thread, so it is not recommended to create any Swing component in a constructor.
 * Use the <a href="https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html">EDT</a> instead.
 *
 * @author lesya
 */
public interface UnnamedConfigurable {
  /**
   * Creates new Swing form that enables user to configure the settings.
   * Usually this method is called on the EDT, so it should not take a long time.
   * <p>
   * Also this place is designed to allocate resources (subscriptions/listeners etc.)
   *
   * @return new Swing form to show, or {@code null} if it cannot be created
   * @see #disposeUIResources
   */
  @Nullable
  JComponent createComponent();

  /**
   * @return component which should be focused when the dialog appears on the screen.
   */
  default @Nullable JComponent getPreferredFocusedComponent() {
    return null;
  }

  /**
   * Indicates whether the Swing form was modified or not.
   * This method is called very often, so it should not take a long time.
   *
   * @return {@code true} if the settings were modified, {@code false} otherwise
   */
  boolean isModified();

  /**
   * Stores the settings from the Swing form to the configurable component.
   * This method is called on EDT upon user's request.
   *
   * @throws ConfigurationException if values cannot be applied
   */
  void apply() throws ConfigurationException;

  /**
   * Loads the settings from the configurable component to the Swing form.
   * This method is called on EDT immediately after the form creation or later upon user's request.
   */
  default void reset() {
  }

  /**
   * Notifies the configurable component that the Swing form will be closed.
   * This method should dispose all resources associated with the component.
   */
  default void disposeUIResources() {
  }

  /**
   * Called when 'Cancel' is pressed in the Settings dialog.
   */
  default void cancel() {
  }
}
