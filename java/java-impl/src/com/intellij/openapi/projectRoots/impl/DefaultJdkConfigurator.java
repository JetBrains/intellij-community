// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class DefaultJdkConfigurator implements ApplicationComponent {
  private final JavaSdk myJavaSdk;
  private final PropertiesComponent myPropertiesComponent;
  private final ProjectJdkTable myProjectJdkTable;

  public DefaultJdkConfigurator(JavaSdk javaSdk, PropertiesComponent propertiesComponent, ProjectJdkTable projectJdkTable) {
    myJavaSdk = javaSdk;
    myPropertiesComponent = propertiesComponent;
    myProjectJdkTable = projectJdkTable;
  }

  @Override
  public void initComponent() {
    if (myPropertiesComponent.getBoolean("defaultJdkConfigured", false)) return;
    List<Sdk> jdks = myProjectJdkTable.getSdksOfType(myJavaSdk);
    if (jdks.isEmpty()) {
      Collection<String> homePaths = myJavaSdk.suggestHomePaths();
      if (homePaths.isEmpty()) return;
      String homePath = ContainerUtil.find(homePaths, path -> isLTS(path));
      if (homePath == null) homePath = homePaths.iterator().next();
      if (homePath != null && myJavaSdk.isValidSdkHome(homePath)) {
        String suggestedName = myJavaSdk.suggestSdkName(null, homePath);
        if (suggestedName != null) {
          String finalHomePath = homePath;
          ApplicationManager.getApplication().runWriteAction(() ->
            myProjectJdkTable.addJdk(myJavaSdk.createJdk(suggestedName, finalHomePath, false))
          );
        }
      }
    }
    myPropertiesComponent.setValue("defaultJdkConfigured", true);
  }

  private static boolean isLTS(String path) {
    JavaSdkVersion version = JavaSdkVersion.fromVersionString(path);
    return version != null && version.isLongTermSupport();
  }
}
