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
package com.intellij.compiler.options;

import com.intellij.compiler.CompilerSettingsFactory;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CompilerConfigurable implements SearchableConfigurable.Parent, Configurable.NoScroll {

  private final Project myProject;
  private final CompilerUIConfigurable myCompilerUIConfigurable;
  private Configurable[] myKids;

  public CompilerConfigurable(Project project) {
    myProject = project;
    myCompilerUIConfigurable = new CompilerUIConfigurable(myProject);
  }

  public String getDisplayName() {
    return CompilerBundle.message("compiler.configurable.display.name");
  }

  public String getHelpTopic() {
    return "project.propCompiler";
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  public JComponent createComponent() {
    return myCompilerUIConfigurable.createComponent();
  }

  public boolean hasOwnContent() {
    return true;
  }

  public boolean isVisible() {
    return true;
  }

  public boolean isModified() {
    return myCompilerUIConfigurable.isModified();
  }

  public void apply() throws ConfigurationException {
    myCompilerUIConfigurable.apply();
  }

  public void reset() {
    myCompilerUIConfigurable.reset();
  }

  public void disposeUIResources() {
    myCompilerUIConfigurable.disposeUIResources();
  }

  public Configurable[] getConfigurables() {
    if (myKids == null) {
      final CompilerSettingsFactory[] factories = Extensions.getExtensions(CompilerSettingsFactory.EP_NAME, myProject);
      myKids = ContainerUtil.mapNotNull(factories, new NullableFunction<CompilerSettingsFactory, Configurable>() {
        @Nullable
        @Override
        public Configurable fun(CompilerSettingsFactory factory) {
            return factory.create(myProject);
        }
      }, new Configurable[0]);
    }

    return myKids;
  }
}
