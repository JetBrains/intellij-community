/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.facet.impl.ui.facetType;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.facet.impl.invalid.InvalidFacetType;
import com.intellij.facet.ui.DefaultFacetSettingsEditor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.UnnamedConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FacetTypeEditor extends UnnamedConfigurableGroup {
  private Configurable myDefaultSettingsConfigurable;
  private final Disposable myDisposable = Disposer.newDisposable();

  public <C extends FacetConfiguration> FacetTypeEditor(@NotNull Project project, @NotNull FacetType<?, C> facetType) {
    if (!(facetType instanceof InvalidFacetType)) {
      C configuration = ProjectFacetManager.getInstance(project).createDefaultConfiguration(facetType);
      DefaultFacetSettingsEditor defaultSettingsEditor = facetType.createDefaultConfigurationEditor(project, configuration);
      if (defaultSettingsEditor != null) {
        myDefaultSettingsConfigurable = new DefaultFacetSettingsConfigurable<>(facetType, project, defaultSettingsEditor, configuration);
        add(myDefaultSettingsConfigurable);
      }
    }
  }

  public boolean isVisible() {
    return myDefaultSettingsConfigurable != null;
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myDisposable);
    super.disposeUIResources();
  }

  @Override
  public JComponent createComponent() {
    if (myDefaultSettingsConfigurable != null) {
      return myDefaultSettingsConfigurable.createComponent();
    }
    else {
      return new JPanel();
    }
  }

  @Nullable
  public String getHelpTopic() {
    return myDefaultSettingsConfigurable != null ? myDefaultSettingsConfigurable.getHelpTopic() : null;
  }
}
