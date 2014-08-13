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
package com.intellij.remoteServer.impl.module;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import org.jetbrains.annotations.Nullable;


public abstract class CloudModuleBuilderContribution {

  private final CloudModuleBuilder myModuleBuilder;

  private final ServerType<?> myCloudType;
  private CloudApplicationConfigurable myApplicationConfigurable;

  public CloudModuleBuilderContribution(CloudModuleBuilder moduleBuilder, ServerType<?> cloudType) {
    myModuleBuilder = moduleBuilder;
    myCloudType = cloudType;
  }

  protected CloudModuleBuilder getModuleBuilder() {
    return myModuleBuilder;
  }

  protected ServerType<?> getCloudType() {
    return myCloudType;
  }

  public CloudApplicationConfigurable getApplicationConfigurable(@Nullable Project project, Disposable parentDisposable) {
    if (myApplicationConfigurable == null) {
      myApplicationConfigurable = createApplicationConfigurable(project, parentDisposable);
    }
    return myApplicationConfigurable;
  }

  public void preConfigureModule(Module module, ModifiableRootModel model) {

  }

  public abstract void configureModule(Module module);

  protected abstract CloudApplicationConfigurable createApplicationConfigurable(@Nullable Project project, Disposable parentDisposable);

  protected DeploymentConfiguration createDeploymentConfiguration(DeploymentSource deploymentSource) {
    return myCloudType.createDeploymentConfigurator(null).createDefaultConfiguration(deploymentSource);
  }
}
