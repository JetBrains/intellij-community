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
package com.intellij.facet.impl.ui.facetType;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.facet.ui.DefaultFacetSettingsEditor;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DefaultFacetSettingsConfigurable<C extends FacetConfiguration> implements Configurable {
  private final FacetType<?, ? super C> myFacetType;
  private final Project myProject;
  private final DefaultFacetSettingsEditor myDelegate;
  private final C myConfiguration;

  public DefaultFacetSettingsConfigurable(final @NotNull FacetType<?, ? super C> facetType, final @NotNull Project project, final @NotNull DefaultFacetSettingsEditor delegate,
                                          @NotNull C configuration) {
    myFacetType = facetType;
    myProject = project;
    myDelegate = delegate;
    myConfiguration = configuration;
  }

  @Override
  public String getDisplayName() {
    return JavaUiBundle.message("facet.defaults.display.name");
  }

  @Override
  public String getHelpTopic() {
    return myDelegate.getHelpTopic();
  }

  @Override
  public JComponent createComponent() {
    return myDelegate.createComponent();
  }

  @Override
  public boolean isModified() {
    return myDelegate.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myDelegate.isModified()) {
      myDelegate.apply();
      ProjectFacetManager.getInstance(myProject).setDefaultConfiguration(myFacetType, myConfiguration);
    }
  }

  @Override
  public void reset() {
    myDelegate.reset();
  }

  @Override
  public void disposeUIResources() {
    myDelegate.disposeUIResources();
  }
}
