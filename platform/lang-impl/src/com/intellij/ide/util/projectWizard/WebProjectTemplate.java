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

import com.intellij.openapi.module.*;
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

  private final NotNullLazyValue<GeneratorPeer<T>> myPeerHolder = new NotNullLazyValue<GeneratorPeer<T>>() {
    @NotNull
    @Override
    protected GeneratorPeer<T> compute() {
      return createPeer();
    }
  };

  @NotNull
  @Override
  public ModuleBuilder createModuleBuilder() {
    return WebModuleType.getInstance().createModuleBuilder(this);
  }

  @Nullable
  @Override
  public ValidationInfo validateSettings() {
    return myPeerHolder.getValue().validate();
  }

  public Icon getIcon() {
    return WebModuleBuilder.ICON;
  }

  @NotNull
  public GeneratorPeer<T> getPeer() {
    return myPeerHolder.getValue();
  }
}
