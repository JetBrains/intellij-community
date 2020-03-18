// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.util.BuildNumber;

/**
 * @author yole
 */
public class PluginRepositoryRequests {
  public static String getBuildForPluginRepositoryRequests() {
    ApplicationInfoEx instance = ApplicationInfoImpl.getShadowInstance();
    String compatibleBuild = PluginManagerCore.getPluginsCompatibleBuild();
    if (compatibleBuild != null) {
      return BuildNumber.fromStringWithProductCode(compatibleBuild, instance.getBuild().getProductCode()).asString();
    }
    return instance.getApiVersion();
  }
}
