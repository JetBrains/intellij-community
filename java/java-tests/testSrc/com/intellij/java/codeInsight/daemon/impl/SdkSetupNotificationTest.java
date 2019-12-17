// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.UnknownSdkEditorNotification;
import com.intellij.openapi.projectRoots.impl.UnknownSdkEditorNotification.SdkFixInfo;
import com.intellij.openapi.projectRoots.impl.UnknownSdkTracker;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Pavel.Dolgov
 */
public class SdkSetupNotificationTest extends JavaCodeInsightFixtureTestCase {

  public void testProjectSdk() {
    Sdk sdk = IdeaTestUtil.getMockJdk18();
    setProjectSdk(sdk);
    ModuleRootModificationUtil.setSdkInherited(getModule());

    final List<SdkFixInfo> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .isEmpty();
  }

  public void testMissingProjectJdk() {
    WriteAction.run(() -> {
      ProjectRootManager.getInstance(getProject()).setProjectSdkName("missingSDK", JavaSdk.getInstance().getName());
    });

    final List<SdkFixInfo> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .hasSize(1)
      .first()
      .returns("missingSDK", SdkFixInfo::getSdkName);
  }

  public void testMissingModuleJdk() {
    WriteAction.run(() -> {
      ModifiableRootModel model = ModuleRootManager.getInstance(getModule()).getModifiableModel();
      model.setInvalidSdk("missingSDK", JavaSdk.getInstance().getName());
      model.commit();
    });

    final List<SdkFixInfo> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .hasSize(1)
      .first()
      .returns("missingSDK", SdkFixInfo::getSdkName);
  }

  public void testNoProjectSdk() {
    setProjectSdk(null);
    ModuleRootModificationUtil.setSdkInherited(getModule());

    final List<SdkFixInfo> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .isNotEmpty();

    /*
    final IntentionActionWithOptions action = fixes.getIntentionAction();
    assertThat(action).isNotNull();
    final String text = action.getText();
    assertThat(text).isNotNull();
    if (!text.startsWith("Setup SDK")) {
      final int length = Math.min(text.length(), "Setup SDK".length());
      assertThat(text.substring(0, length)).isEqualTo("Setup SDK");
    }*/
  }

  public void testModuleSdk() {
    Sdk sdk = IdeaTestUtil.getMockJdk18();
    ModuleRootModificationUtil.setModuleSdk(getModule(), sdk);

    final List<SdkFixInfo> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .isEmpty();
  }

  public void testNoModuleSdk() {
    ModuleRootModificationUtil.setModuleSdk(getModule(), null);

    final List<SdkFixInfo> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .isNotEmpty();

    /*final IntentionActionWithOptions action = panel.getIntentionAction();
    assertThat(action).isNotNull();
    final String text = action.getText();
    assertThat(text).isNotNull();
    if (!text.startsWith("Setup SDK")) {
      final int length = Math.min(text.length(), "Setup SDK".length());
      assertThat(text.substring(0, length)).isEqualTo("Setup SDK");
    }*/
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    setProjectSdk(IdeaTestUtil.getMockJdk17());
  }

  @NotNull
  private List<SdkFixInfo> detectMissingSdks() {
    UnknownSdkTracker.getInstance(getProject()).updateUnknownSdks();

    return UnknownSdkEditorNotification.getInstance(getProject()).getNotifications();
  }

  private void setProjectSdk(@Nullable Sdk sdk) {
    WriteAction.run(() -> {
      ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();

      if (sdk != null) {
        final Sdk foundJdk = jdkTable.findJdk(sdk.getName());
        if (foundJdk == null) {
          jdkTable.addJdk(sdk, myFixture.getProjectDisposable());
        }
      }

      ProjectRootManager.getInstance(getProject()).setProjectSdk(sdk);
    });
  }
}
