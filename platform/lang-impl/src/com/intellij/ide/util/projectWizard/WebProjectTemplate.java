/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.WebModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.WebProjectGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 9/28/12
 */
public abstract class WebProjectTemplate<T> extends WebProjectGenerator<T> implements ProjectTemplate {

  protected final NotNullLazyValue<GeneratorPeer<T>> myPeer = new NotNullLazyValue<GeneratorPeer<T>>() {
    @NotNull
    @Override
    protected GeneratorPeer<T> compute() {
      return createPeer();
    }
  };

  @Override
  public JComponent getSettingsPanel() {
    return myPeer.getValue().getComponent();
  }

  @NotNull
  @Override
  public ModuleBuilder createModuleBuilder() {
    final ModuleBuilder builder = WebModuleType.getInstance().createModuleBuilder();
    return new ModuleBuilder() {
      @Override
      public void setupRootModel(ModifiableRootModel modifiableRootModel) throws ConfigurationException {
        builder.setupRootModel(modifiableRootModel);
      }

      @Override
      public ModuleType getModuleType() {
        return builder.getModuleType();
      }

      @Override
      public List<Module> commit(Project project, ModifiableModuleModel model, ModulesProvider modulesProvider) {
        List<Module> modules = builder.commit(project, model, modulesProvider);
        if (modules != null && !modules.isEmpty()) {
          Module module = modules.get(0);
          generateProject(module.getProject(), module.getProject().getBaseDir(), myPeer.getValue().getSettings(), module);
        }
        return modules;
      }
    };
  }

  @Nullable
  @Override
  public ValidationInfo validateSettings() {
    return myPeer.getValue().validate();
  }
}
