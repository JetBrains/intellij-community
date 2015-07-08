/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.options;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * This interface represents a configurable component that provides a Swing form to configure some settings.
 * Sometimes IDEA instantiates it on a background thread, so it is not recommended to create any Swing component in a constructor.
 * Use the <a href="https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html">EDT</a> instead.
 *
 * @author lesya
 */
public interface UnnamedConfigurable {
  /**
   * Creates new Swing form that enables user to configure the settings.
   * Usually this method is called on the EDT, so it should not take a long time.
   *
   * Also this place is designed to allocate resources (subscriptions/listeners etc.)
   * @see #disposeUIResources
   *
   * @return new Swing form to show, or {@code null} if it cannot be created
   */
  @Nullable
  JComponent createComponent();

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
  void reset();

  /**
   * Notifies the configurable component that the Swing form will be closed.
   * This method should dispose all resources associated with the component.
   */
  void disposeUIResources();
}
