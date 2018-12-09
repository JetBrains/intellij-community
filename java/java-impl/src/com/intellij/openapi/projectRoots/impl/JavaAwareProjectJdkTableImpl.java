// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.util.SystemProperties;
import com.intellij.util.lang.JavaVersion;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.model.java.JdkVersionDetector;

public class JavaAwareProjectJdkTableImpl extends ProjectJdkTableImpl {
  public static JavaAwareProjectJdkTableImpl getInstanceEx() {
    return (JavaAwareProjectJdkTableImpl)ServiceManager.getService(ProjectJdkTable.class);
  }

  private final JavaSdk myJavaSdk;
  private Sdk myInternalJdk;

  public JavaAwareProjectJdkTableImpl(@NotNull JavaSdk javaSdk) {
    myJavaSdk = javaSdk;
  }

  @NotNull
  public Sdk getInternalJdk() {
    if (myInternalJdk == null) {
      String jdkHome = SystemProperties.getJavaHome();
      String versionName = JdkVersionDetector.formatVersionString(JavaVersion.current());
      myInternalJdk = myJavaSdk.createJdk(versionName, jdkHome);
    }
    return myInternalJdk;
  }

  @Override
  public void removeJdk(@NotNull Sdk jdk) {
    super.removeJdk(jdk);
    if (jdk.equals(myInternalJdk)) {
      myInternalJdk = null;
    }
  }

  @NotNull
  @Override
  public SdkTypeId getDefaultSdkType() {
    return myJavaSdk;
  }

  @Override
  public void loadState(@NotNull Element element) {
    myInternalJdk = null;
    try {
      super.loadState(element);
    }
    finally {
      getInternalJdk();
    }
  }

  @Override
  protected String getSdkTypeName(String type) {
    return type != null ? type : JavaSdk.getInstance().getName();
  }

  @TestOnly
  public static void removeInternalJdkInTests() {
    WriteAction.run(()-> {
      JavaAwareProjectJdkTableImpl table = getInstanceEx();
      if (table.myInternalJdk != null) {
        table.removeJdk(table.myInternalJdk);
      }
    });
  }
}