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
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;

public class JreVersionDetector {
  private String myLastAlternativeJrePath = null; //awful hack
  private boolean myLastIsJre50;

  public <T extends ModuleBasedConfiguration & CommonJavaRunConfigurationParameters> boolean isJre50Configured(final T configuration) {
    if (configuration.isAlternativeJrePathEnabled()) {
      if (configuration.getAlternativeJrePath().equals(myLastAlternativeJrePath)) return myLastIsJre50;
      myLastAlternativeJrePath = configuration.getAlternativeJrePath();
      final String versionString = JavaSdkImpl.getJdkVersion(myLastAlternativeJrePath);
      myLastIsJre50 = versionString != null && isJre50(versionString);
      return myLastIsJre50;
    } else {
      final Module module = configuration.getConfigurationModule().getModule();
      if (module != null && !module.isDisposed()) {
        final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        final Sdk jdk = rootManager.getSdk();
        return isJre50(jdk);
      }

      final Sdk projectJdk = ProjectRootManager.getInstance(configuration.getProject()).getProjectJdk();
      return isJre50(projectJdk);
    }
  }

  private static boolean isJre50(final Sdk jdk) {
    if (jdk == null) return false;
    final String versionString = jdk.getVersionString();
    return versionString != null && isJre50(versionString);
  }

  private static boolean isJre50(final String versionString) {
    return versionString.contains("5.0") || versionString.contains("1.5") || versionString.contains("1.6") || versionString.contains("1.7");
  }
}