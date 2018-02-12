/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.util;

import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.jps.model.java.JdkVersionDetector;

public class JreVersionDetector {
  private String myLastAlternativeJrePath ; //awful hack
  private boolean myLastIsJre50;

  public boolean isModuleJre50Configured(final ModuleBasedConfiguration configuration) {
    Module module = configuration.getConfigurationModule().getModule();
    Sdk sdk = module != null && !module.isDisposed() ? ModuleRootManager.getInstance(module).getSdk()
                                                     : ProjectRootManager.getInstance(configuration.getProject()).getProjectSdk();
    return JavaSdkUtil.isJdkAtLeast(sdk, JavaSdkVersion.JDK_1_5);
  }

  public boolean isJre50Configured(final CommonJavaRunConfigurationParameters configuration) {
    if (configuration.isAlternativeJrePathEnabled()) {
      String alternativeJrePath = configuration.getAlternativeJrePath();
      if (!StringUtil.isEmptyOrSpaces(alternativeJrePath)) {
        if (Comparing.equal(alternativeJrePath, myLastAlternativeJrePath)) {
          return myLastIsJre50;
        }

        myLastAlternativeJrePath = alternativeJrePath;
        JdkVersionDetector.JdkVersionInfo jdkInfo = SdkVersionUtil.getJdkVersionInfo(myLastAlternativeJrePath);
        myLastIsJre50 = jdkInfo != null && jdkInfo.version.feature >= 5;
        return myLastIsJre50;
      }
    }

    return false;
  }
}