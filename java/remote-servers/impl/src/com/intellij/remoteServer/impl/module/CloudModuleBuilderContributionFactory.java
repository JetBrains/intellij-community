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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.remoteServer.ServerType;


public abstract class CloudModuleBuilderContributionFactory {

  public static final ExtensionPointName<CloudModuleBuilderContributionFactory> EP_NAME
    = ExtensionPointName.create("com.intellij.remoteServer.moduleBuilderContribution");

  public abstract ServerType<?> getCloudType();

  public abstract CloudModuleBuilderContribution createContribution(CloudModuleBuilder moduleBuilder);

  public static CloudModuleBuilderContributionFactory getInstanceByType(ServerType<?> cloudType) {
    for (CloudModuleBuilderContributionFactory contribution : EP_NAME.getExtensions()) {
      if (contribution.getCloudType().equals(cloudType)) {
        return contribution;
      }
    }
    return null;
  }
}
