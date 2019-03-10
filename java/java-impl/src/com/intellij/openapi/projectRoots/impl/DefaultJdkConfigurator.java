// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collection;

final class DefaultJdkConfigurator implements ApplicationInitializedListener {
  DefaultJdkConfigurator() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw ExtensionNotApplicableException.INSTANCE;
    }
  }

  @Override
  public void componentsInitialized() {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();

    if (propertiesComponent.getBoolean("defaultJdkConfigured", false)) {
      return;
    }

    ProjectJdkTable projectJdkTable = ProjectJdkTable.getInstance();
    JavaSdk javaSdk = JavaSdk.getInstance();
    if (projectJdkTable.getSdksOfType(javaSdk).isEmpty()) {
      Collection<String> homePaths = javaSdk.suggestHomePaths();
      String homePath = ContainerUtil.getFirstItem(homePaths);
      if (homePath != null && javaSdk.isValidSdkHome(homePath)) {
        String suggestedName = JdkUtil.suggestJdkName(javaSdk.getVersionString(homePath));
        if (suggestedName != null) {
          ApplicationManager.getApplication().runWriteAction(
            () -> projectJdkTable.addJdk(javaSdk.createJdk(suggestedName, homePath, false))
          );
        }
      }
    }
    propertiesComponent.setValue("defaultJdkConfigured", true);
  }
}