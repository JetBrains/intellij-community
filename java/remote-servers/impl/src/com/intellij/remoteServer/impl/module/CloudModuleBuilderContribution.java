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
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import org.jetbrains.annotations.Nullable;


public abstract class CloudModuleBuilderContribution {

  public static final ExtensionPointName<CloudModuleBuilderContribution> EP_NAME
    = ExtensionPointName.create("com.intellij.remoteServer.moduleBuilderContribution");

  public abstract ServerType<?> getCloudType();

  public abstract CloudApplicationConfigurable createApplicationConfigurable(@Nullable Project project, Disposable parentDisposable);

  public abstract void configureModule(Module module,
                                       RemoteServer<?> account,
                                       CloudApplicationConfiguration configuration);

  public static CloudModuleBuilderContribution getInstanceByType(ServerType<?> cloudType) {
    for (CloudModuleBuilderContribution contribution : EP_NAME.getExtensions()) {
      if (contribution.getCloudType() == cloudType) {
        return contribution;
      }
    }
    return null;
  }
}
