/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.execution;

import com.intellij.execution.configuration.RunConfigurationExtensionsManager;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

public class JavaRunConfigurationExtensionManager extends RunConfigurationExtensionsManager<RunConfigurationBase, RunConfigurationExtension> {
  private static final Logger LOG = Logger.getInstance(RunConfigurationExtension.class);

  public JavaRunConfigurationExtensionManager() {
    super(RunConfigurationExtension.EP_NAME);
  }

  public static JavaRunConfigurationExtensionManager getInstance() {
     return ServiceManager.getService(JavaRunConfigurationExtensionManager.class);
   }

  public static void checkConfigurationIsValid(RunConfigurationBase configuration) {
    try {
      getInstance().validateConfiguration(configuration, false);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @NotNull
  @Override
  protected String getIdAttrName() {
    return "name";
  }

  @NotNull
  @Override
  protected String getExtensionRootAttr() {
    return "extension";
  }
}
