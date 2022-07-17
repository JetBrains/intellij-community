// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.idea.TestFor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.UnknownSdkEditorNotification;
import com.intellij.openapi.projectRoots.impl.UnknownSdkFix;
import com.intellij.openapi.projectRoots.impl.UnknownSdkTracker;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkResolver;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class UnknownSdkTrackerTest extends JavaCodeInsightFixtureTestCase {
  public void testProjectSdk() {
    Sdk sdk = IdeaTestUtil.getMockJdk18();
    setProjectSdk2(sdk);
    ModuleRootModificationUtil.setSdkInherited(getModule());

    final List<String> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .isEmpty();
  }

  public void testMissingProjectJdk() {
    WriteAction.run(() -> {
      ProjectRootManager.getInstance(getProject()).setProjectSdkName("missingSDK", JavaSdk.getInstance().getName());
    });

    final List<String> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .hasSize(1)
      .first().asString().startsWith("SdkFixInfo:");
  }

  public void testMissingProjectUnknownJdk() {
    WriteAction.run(() -> {
      ProjectRootManager.getInstance(getProject()).setProjectSdkName("missingSDK", "foo-bar-baz");
    });

    final List<String> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .isEmpty();
  }

  public void testMissingModuleJdk() {
    setModuleSdk("missingSDK", JavaSdk.getInstance());

    final List<String> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .hasSize(1)
      .first().asString().startsWith("SdkFixInfo:");
  }

  public void testMissingModuleUnknownSdk() {
    setModuleSdk("missingSDK", "foo-bar-baz");

    final List<String> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .hasSize(1)
      .first().asString().startsWith("SdkSetupNotification:");
  }

  public void testNoProjectSdk() {
    setProjectSdk2(null);
    ModuleRootModificationUtil.setSdkInherited(getModule());

    final List<String> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .isNotEmpty();
  }

  public void testModuleSdk() {
    Sdk sdk = addSdkIfNeeded(IdeaTestUtil.getMockJdk18());
    ModuleRootModificationUtil.setModuleSdk(getModule(), sdk);

    final List<String> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .isEmpty();
  }

  public void testNoModuleSdk() {
    ModuleRootModificationUtil.setModuleSdk(getModule(), null);

    final List<String> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .isNotEmpty();
  }

  @TestFor(issues = "IDEA-236153")
  public void testItIgnoresSameSnapshot() {
    AtomicInteger lookupCalls = new AtomicInteger();
    UnknownSdkResolver.EP_NAME.getPoint().registerExtension(new UnknownSdkResolver() {
      @Override
      public boolean supportsResolution(@NotNull SdkTypeId sdkTypeId) {
        return true;
      }

      @Override
      public @Nullable UnknownSdkLookup createResolver(@Nullable Project project, @NotNull ProgressIndicator indicator) {
        lookupCalls.incrementAndGet();
        return null;
      }
    }, getTestRootDisposable());

    setModuleSdk("foo-bar-baz", JavaSdk.getInstance());

    detectMissingSdks();
    detectMissingSdks();
    detectMissingSdks();

    assertThat(lookupCalls).hasValue(1);

    //do the same change
    setModuleSdk("foo-bar-baz", JavaSdk.getInstance());
    detectMissingSdks();
    detectMissingSdks();
    detectMissingSdks();

    //it must not re-compute it
    assertThat(lookupCalls).hasValue(1);

    //change it finally
    setModuleSdk("foo-bar-baz-NEW", JavaSdk.getInstance());
    detectMissingSdks();
    detectMissingSdks();
    detectMissingSdks();

    //it must not re-compute it
    assertThat(lookupCalls).hasValue(2);
  }


  @TestFor(issues = "IDEA-237884")
  public void testShouldNotRantOnCustomSDKType() {
    final Sdk broken = ProjectJdkTable.getInstance().createSdk("broken-sdk-123", SimpleJavaSdkType.getInstance());
    WriteAction.run(() -> {
      SdkModificator m = broken.getSdkModificator();
      m.setHomePath("invalid home path");
      m.setVersionString("11");
      m.commitChanges();

      ProjectJdkTable.getInstance().addJdk(broken, getTestRootDisposable());

      ModifiableRootModel mod = ModuleRootManager.getInstance(getModule()).getModifiableModel();
      mod.setSdk(broken);
      mod.commit();

    });
    final @NotNull List<String> panel = detectMissingSdks();
    assertThat(panel).isEmpty();
  }

  private class SdkTestCases {
    final Sdk broken = ProjectJdkTable.getInstance().createSdk("broken-sdk-123", JavaSdk.getInstance());
    final Sdk valid = IdeaTestUtil.getMockJdk18();

    private SdkTestCases() {
      WriteAction.run(() -> {
        SdkModificator m = broken.getSdkModificator();
        m.setHomePath("invalid home path");
        m.setVersionString("11");
        m.commitChanges();

        ProjectJdkTable.getInstance().addJdk(broken, getTestRootDisposable());
        ProjectJdkTable.getInstance().addJdk(valid, getTestRootDisposable());
      });
    }
  }

  public void testBrokenModuleSdk() {
    new SdkTestCases() {
      {
        WriteAction.run(() -> {
          ModifiableRootModel m = ModuleRootManager.getInstance(getModule()).getModifiableModel();
          m.setSdk(broken);
          m.commit();

          ProjectRootManager.getInstance(getProject()).setProjectSdk(valid);
        });

        final List<String> panel = detectMissingSdks();
        assertThat(panel).hasSize(1).first().asString().startsWith("SdkFixInfo:InvalidSdkFixInfo { name: " + broken.getName());
      }
    };
  }

  public void testValidProjectSdk() {
    new SdkTestCases() {
      {
        WriteAction.run(() -> {
          ModifiableRootModel m = ModuleRootManager.getInstance(getModule()).getModifiableModel();
          m.inheritSdk();
          m.commit();

          ProjectRootManager.getInstance(getProject()).setProjectSdk(valid);
        });

        final List<String> panel = detectMissingSdks();
        assertThat(panel).isEmpty();
      }
    };
  }

  public void testBrokenProjectValidModuleSdk() {
    new SdkTestCases() {
      {
        WriteAction.run(() -> {
          ModifiableRootModel m = ModuleRootManager.getInstance(getModule()).getModifiableModel();
          m.setSdk(valid);
          m.commit();

          ProjectRootManager.getInstance(getProject()).setProjectSdk(broken);
        });

        final List<String> panel = detectMissingSdks();
        assertThat(panel).hasSize(1).first().asString().startsWith("SdkFixInfo:InvalidSdkFixInfo { name: " + broken.getName());
      }
    };
  }

  private void setModuleSdk(@NotNull String sdkName, @NotNull SdkTypeId type) {
    setModuleSdk(sdkName, type.getName());
  }

  private void setModuleSdk(@NotNull String sdkName, @NotNull String sdkType) {
    WriteAction.run(() -> {
      ModifiableRootModel model = ModuleRootManager.getInstance(getModule()).getModifiableModel();
      model.setInvalidSdk(sdkName, sdkType);
      model.commit();
    });
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    setProjectSdk2(IdeaTestUtil.getMockJdk17());
  }

  private @NotNull List<String> detectMissingSdks() {
    UnknownSdkTracker.getInstance(getProject()).updateUnknownSdks();

    ArrayList<String> infos = new ArrayList<>();
    EditorNotificationPanel sdkNotification =
      SdkSetupNotificationTestBase.runOnText(myFixture, "Sample.java", "class Sample { java.lang.String foo; }");
    if (sdkNotification != null) {
      infos.add("SdkSetupNotification:" + sdkNotification.getText());
    }

    for (UnknownSdkFix notification : UnknownSdkEditorNotification.getInstance(getProject()).getNotifications()) {
      infos.add("SdkFixInfo:" + notification.toString());
    }

    return infos;
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
