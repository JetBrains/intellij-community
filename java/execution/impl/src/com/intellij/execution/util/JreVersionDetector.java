/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 03.08.2006
 * Time: 14:01:20
 */
package com.intellij.execution.util;

import com.intellij.execution.RunJavaConfiguration;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;

public class JreVersionDetector {
  private String myLastAlternativeJrePath = null; //awful hack
  private boolean myLastIsJre50;

  public <T extends ModuleBasedConfiguration & RunJavaConfiguration> boolean isJre50Configured(final T configuration) {
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
    return versionString.contains("5.0") || versionString.contains("1.5") || versionString.contains("1.6");
  }
}