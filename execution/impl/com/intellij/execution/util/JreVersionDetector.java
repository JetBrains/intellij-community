/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 03.08.2006
 * Time: 14:01:20
 */
package com.intellij.execution.util;

import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.impl.JdkVersionUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;

public class JreVersionDetector {
  private String myLastAlternativeJrePath = null; //awful hack
  private boolean myLastIsJre50;

  public boolean isJre50Configured(final ApplicationConfiguration configuration) {
    return isJre50Configured(configuration, configuration.ALTERNATIVE_JRE_PATH_ENABLED, configuration.ALTERNATIVE_JRE_PATH);
  }

  public boolean isJre50Configured(final JUnitConfiguration configuration) {
    return isJre50Configured(configuration, configuration.ALTERNATIVE_JRE_PATH_ENABLED, configuration.ALTERNATIVE_JRE_PATH);
  }

  public boolean isJre50Configured(final ModuleBasedConfiguration configuration, final boolean altPathEnabled, final String jrePath) {
    if (altPathEnabled) {
      if (jrePath.equals(myLastAlternativeJrePath)) return myLastIsJre50;
      myLastAlternativeJrePath = jrePath;
      final String versionString = JdkVersionUtil.getJdkVersion(jrePath);
      myLastIsJre50 = versionString != null && isJre50(versionString);
      return myLastIsJre50;
    } else {
      final Module module = configuration.getConfigurationModule().getModule();
      if (module != null && !module.isDisposed()) {
        final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        final ProjectJdk jdk = rootManager.getJdk();
        return isJre50(jdk);
      }

      final ProjectJdk projectJdk = ProjectRootManager.getInstance(configuration.getProject()).getProjectJdk();
      return isJre50(projectJdk);
    }
  }

  private static boolean isJre50(final ProjectJdk jdk) {
    if (jdk == null) return false;
    final String versionString = jdk.getVersionString();
    return versionString != null && isJre50(versionString);
  }

  private static boolean isJre50(final String versionString) {
    return versionString.contains("5.0") || versionString.contains("1.5") || versionString.contains("1.6");
  }
}