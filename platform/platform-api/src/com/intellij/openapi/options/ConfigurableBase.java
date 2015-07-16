/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ConfigurableBase<UI extends ConfigurableUi<S>, S> implements SearchableConfigurable, Configurable.NoScroll {
  private final String id;
  private final String displayName;
  private final String helpTopic;

  private UI ui;

  protected ConfigurableBase(@NotNull String id, @NotNull String displayName, @Nullable String helpTopic) {
    this.id = id;
    this.displayName = displayName;
    this.helpTopic = helpTopic;
  }

  @NotNull
  @Override
  public final String getId() {
    return id;
  }

  @Nls
  @Override
  public final String getDisplayName() {
    return displayName;
  }

  @Nullable
  @Override
  public final String getHelpTopic() {
    return helpTopic;
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @NotNull
  protected abstract S getSettings();

  @Override
  public void reset() {
    if (ui != null) {
      ui.reset(getSettings());
    }
  }

  @Nullable
  @Override
  public final JComponent createComponent() {
    if (ui == null) {
      ui = createUi();
    }
    return ui.getComponent();
  }

  protected abstract UI createUi();

  @Override
  public final boolean isModified() {
    return ui != null && ui.isModified(getSettings());
  }

  @Override
  public final void apply() throws ConfigurationException {
    if (ui != null) {
      ui.apply(getSettings());
    }
  }

  @Override
  public void disposeUIResources() {
    UI ui = this.ui;
    if (ui != null) {
      this.ui = null;
      if (ui instanceof Disposable) {
        Disposer.dispose((Disposable)ui);
      }
    }
  }
}