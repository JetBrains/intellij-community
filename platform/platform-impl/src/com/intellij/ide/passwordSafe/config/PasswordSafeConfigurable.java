/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.passwordSafe.config;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * A configurable for password safe
 */
public class PasswordSafeConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  /**
   * The settings for the password safe
   */
  final PasswordSafeSettings mySettings;

  /**
   * The option panel to use
   */
  PasswordSafeOptionsPanel myPanel;

  /**
   * The constructor
   *
   * @param settings the password safe settings
   */
  public PasswordSafeConfigurable(@NotNull PasswordSafeSettings settings) {
    mySettings = settings;
  }

  /**
   * {@inheritDoc}
   */
  @Nls
  public String getDisplayName() {
    return "Passwords";
  }

  /**
   * {@inheritDoc}
   */
  public String getHelpTopic() {
    return "reference.ide.settings.password.safe";
  }

  /**
   * {@inheritDoc}
   */
  public JComponent createComponent() {
    myPanel = new PasswordSafeOptionsPanel();
    myPanel.reset(mySettings);
    return myPanel.getRoot();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isModified() {
    return myPanel != null && myPanel.isModified(mySettings);
  }

  /**
   * {@inheritDoc}
   */
  public void apply() throws ConfigurationException {
    myPanel.apply(mySettings);
  }

  /**
   * {@inheritDoc}
   */
  public void reset() {
    myPanel.reset(mySettings);
  }

  /**
   * {@inheritDoc}
   */
  public void disposeUIResources() {
    myPanel = null;
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  public String getId() {
    return "application.passwordSafe";
  }

  /**
   * {@inheritDoc}
   */
  public Runnable enableSearch(String option) {
    return null;
  }
}
