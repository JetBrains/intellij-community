// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.projectRoots.*;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.lang.JavaVersion;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.model.java.JdkVersionDetector;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class JavaAwareProjectJdkTableImpl extends ProjectJdkTableImpl {
  private static final String DEFAULT_JDK_CONFIGURED = "defaultJdkConfigured";

  public static JavaAwareProjectJdkTableImpl getInstanceEx() {
    return (JavaAwareProjectJdkTableImpl)ProjectJdkTable.getInstance();
  }

  private Sdk myInternalJdk;

  @RequiresEdt
  @Override
  public void preconfigure() {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    Application application = ApplicationManager.getApplication();
    if (propertiesComponent.getBoolean(DEFAULT_JDK_CONFIGURED, false) || application.isUnitTestMode()) return;

    try {
      Sdk jdk = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        this::guessJdk, JavaBundle.message("progress.title.detecting.jdk"), true, null);
      if (jdk != null) {
        application.runWriteAction(() -> addJdk(jdk));
      }
    }
    catch (ProcessCanceledException ignored) {
    }
    // If cancelled once, let's avoid subsequent attempts
    // While this detection is usually fast, on some machines it could be slow for strange reasons.
    // e.g., JAVA_HOME points to unreachable network drive. In this case, give up further attempts.
    propertiesComponent.setValue(DEFAULT_JDK_CONFIGURED, true);
  }

  private @Nullable Sdk guessJdk() {
    JavaSdk javaSdk = JavaSdk.getInstance();
    List<Sdk> jdks = getSdksOfType(javaSdk);
    if (!jdks.isEmpty()) return null;
    String homePath = ApplicationManager.getApplication().getService(DefaultJdkConfigurator.class).guessJavaHome();
    if (homePath == null || !javaSdk.isValidSdkHome(homePath)) return null;
    String suggestedName = JdkUtil.suggestJdkName(javaSdk.getVersionString(homePath));
    if (suggestedName == null) return null;
    ProgressManager.checkCanceled();
    return javaSdk.createJdk(suggestedName, homePath, false);
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