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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.WebModuleType;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.WebProjectGenerator;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 *         Date: 9/28/12
 */
public abstract class WebProjectTemplate<T> extends WebProjectGenerator<T> implements ProjectTemplate {

  private final NotNullLazyValue<GeneratorPeer<T>> myPeer = new NotNullLazyValue<GeneratorPeer<T>>() {
    @NotNull
    @Override
    protected GeneratorPeer<T> compute() {
      return createPeer();
    }
  };

  @Override
  public void generateProject(Module module) {
    generateProject(module.getProject(), module.getProject().getBaseDir(), myPeer.getValue().getSettings(), module);
  }

  @Override
  public JComponent getSettingsPanel() {
    return myPeer.getValue().getComponent();
  }

  @Override
  public ModuleType getModuleType() {
    return WebModuleType.getInstance();
  }
}
