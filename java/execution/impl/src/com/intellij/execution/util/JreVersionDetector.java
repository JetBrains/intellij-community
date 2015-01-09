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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 03.08.2006
 * Time: 14:01:20
 */
package com.intellij.execution.util;

import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.Nullable;

public class JreVersionDetector {
  private String myLastAlternativeJrePath ; //awful hack
  private boolean myLastIsJre50;

  public boolean isModuleJre50Configured(final ModuleBasedConfiguration configuration) {
    final Module module = configuration.getConfigurationModule().getModule();
    if (module != null && !module.isDisposed()) {
      return isJre50(ModuleRootManager.getInstance(module).getSdk());
    }
    return isJre50(ProjectRootManager.getInstance(configuration.getProject()).getProjectSdk());
  }

  public boolean isJre50Configured(final CommonJavaRunConfigurationParameters configuration) {
    if (configuration.isAlternativeJrePathEnabled()) {
      if (Comparing.equal(configuration.getAlternativeJrePath(), myLastAlternativeJrePath)) {
        return myLastIsJre50;
      }
      myLastAlternativeJrePath = configuration.getAlternativeJrePath();
      final String versionString = JavaSdk.getJdkVersion(myLastAlternativeJrePath);
      myLastIsJre50 = versionString != null && isJre50(versionString);
      return myLastIsJre50;
    }
    return false;
  }

  private static boolean isJre50(final Sdk jdk) {
    if (jdk == null) return false;
    return JavaSdk.getInstance().isOfVersionOrHigher(jdk, JavaSdkVersion.JDK_1_5);
  }

  private static boolean isJre50(final @Nullable String versionString) {
    if (versionString == null) return false;
    JavaSdkVersion version = JavaSdk.getInstance().getVersion(versionString);
    return version != null && version.isAtLeast(JavaSdkVersion.JDK_1_5);
  }
}