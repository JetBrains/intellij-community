/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.application.ApplicationManager;

public abstract class JavaSdk extends SdkType implements ApplicationComponent {
  public JavaSdk(String name) {
    super(name);
  }

  public static JavaSdk getInstance() {
    return ApplicationManager.getApplication().getComponent(JavaSdk.class);
  }

  public abstract ProjectJdk createJdk(String jdkName, String jdkHome);
}
