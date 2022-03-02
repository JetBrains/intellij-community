// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.*;
import com.intellij.util.SystemProperties;
import com.intellij.util.lang.JavaVersion;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.model.java.JdkVersionDetector;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class JavaAwareProjectJdkTableImpl extends ProjectJdkTableImpl {
  private static final String DEFAULT_JDK_CONFIGURED = "defaultJdkConfigured";

  public static JavaAwareProjectJdkTableImpl getInstanceEx() {
    return (JavaAwareProjectJdkTableImpl)ApplicationManager.getApplication().getService(ProjectJdkTable.class);
  }

  private Sdk myInternalJdk;

  @Override
  public void preconfigure() {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    if (propertiesComponent.getBoolean(DEFAULT_JDK_CONFIGURED, false) ||
        ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    JavaSdk javaSdk = JavaSdk.getInstance();
    List<Sdk> jdks = getSdksOfType(javaSdk);
    if (jdks.isEmpty()) {
      String homePath = ApplicationManager.getApplication().getService(DefaultJdkConfigurator.class).guessJavaHome();
      if (homePath != null && javaSdk.isValidSdkHome(homePath)) {
        String suggestedName = JdkUtil.suggestJdkName(javaSdk.getVersionString(homePath));
        if (suggestedName != null) {
          Sdk jdk = javaSdk.createJdk(suggestedName, homePath, false);
          ApplicationManager.getApplication().runWriteAction(() -> addJdk(jdk));
        }
      }
    }
    propertiesComponent.setValue(DEFAULT_JDK_CONFIGURED, true);
  }

  /**
   * @deprecated Bundled JDK must not be used. See IDEA-225960"
   */
  @NotNull
  @Deprecated(forRemoval = true)
  public Sdk getInternalJdk() {
    if (myInternalJdk == null) {
      Path javaHome = Paths.get(SystemProperties.getJavaHome());
      if (JdkUtil.checkForJre(javaHome) && !JdkUtil.checkForJdk(javaHome)) {
        // handle situation like javaHome="<somewhere>/jdk1.8.0_212/jre" (see IDEA-226353)
        Path javaHomeParent = javaHome.getParent();
        if (javaHomeParent != null && JdkUtil.checkForJre(javaHomeParent) && JdkUtil.checkForJdk(javaHomeParent)) {
          javaHome = javaHomeParent;
        }
      }

      String versionName = JdkVersionDetector.formatVersionString(JavaVersion.current());
      myInternalJdk = JavaSdk.getInstance().createJdk(versionName, javaHome.toAbsolutePath().toString(), !JdkUtil.checkForJdk(javaHome));
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
    return JavaSdk.getInstance();
  }

  @Override
  public void loadState(@NotNull Element element) {
    myInternalJdk = null;
    super.loadState(element);
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