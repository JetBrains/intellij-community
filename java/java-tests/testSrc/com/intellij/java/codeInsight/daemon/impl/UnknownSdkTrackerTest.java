// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.UnknownSdkEditorNotification;
import com.intellij.openapi.projectRoots.impl.UnknownSdkTracker;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class UnknownSdkTrackerTest extends JavaCodeInsightFixtureTestCase {
  public void testProjectSdk() {
    Sdk sdk = IdeaTestUtil.getMockJdk18();
    setProjectSdk2(sdk);
    ModuleRootModificationUtil.setSdkInherited(getModule());

    final List<UnknownSdkEditorNotification.SdkFixInfo> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .isEmpty();
  }

  public void testMissingProjectJdk() {
    WriteAction.run(() -> {
      ProjectRootManager.getInstance(getProject()).setProjectSdkName("missingSDK", JavaSdk.getInstance().getName());
    });

    final List<UnknownSdkEditorNotification.SdkFixInfo> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .hasSize(1);
  }

  public void testMissingModuleJdk() {
    WriteAction.run(() -> {
      ModifiableRootModel model = ModuleRootManager.getInstance(getModule()).getModifiableModel();
      model.setInvalidSdk("missingSDK", JavaSdk.getInstance().getName());
      model.commit();
    });

    final List<UnknownSdkEditorNotification.SdkFixInfo> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .hasSize(1);
  }

  public void testNoProjectSdk() {
    setProjectSdk2(null);
    ModuleRootModificationUtil.setSdkInherited(getModule());

    final List<UnknownSdkEditorNotification.SdkFixInfo> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .isNotEmpty();
  }

  public void testModuleSdk() {
    Sdk sdk = addSdkIfNeeded(IdeaTestUtil.getMockJdk18());
    ModuleRootModificationUtil.setModuleSdk(getModule(), sdk);

    final List<UnknownSdkEditorNotification.SdkFixInfo> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .isEmpty();
  }

  public void testNoModuleSdk() {
    ModuleRootModificationUtil.setModuleSdk(getModule(), null);

    final List<UnknownSdkEditorNotification.SdkFixInfo> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .isNotEmpty();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    setProjectSdk2(IdeaTestUtil.getMockJdk17());
  }

  @NotNull
  private List<UnknownSdkEditorNotification.SdkFixInfo> detectMissingSdks() {
    UnknownSdkTracker.getInstance(getProject()).updateUnknownSdks();

    return UnknownSdkEditorNotification.getInstance(getProject()).getNotifications();
  }

  private void setProjectSdk2(@Nullable Sdk sdk) {
    WriteAction.run(() -> {
      ProjectRootManager.getInstance(getProject()).setProjectSdk(addSdkIfNeeded(sdk));
    });
  }

  @Contract("null->null;!null->!null")
  private Sdk addSdkIfNeeded(Sdk sdk) {
    if (sdk == null) return null;

    ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();

    final Sdk foundJdk = jdkTable.findJdk(sdk.getName());
    if (foundJdk != null) return sdk;

    ThrowableRunnable<RuntimeException> addSdk = () -> {
      jdkTable.addJdk(sdk, myFixture.getProjectDisposable());
    };

    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      addSdk.run();
    } else {
      WriteAction.run(addSdk);
    }

    return sdk;
  }
}
