// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.idea.TestFor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.openapi.projectRoots.impl.UnknownSdkEditorNotification;
import com.intellij.openapi.projectRoots.impl.UnknownSdkFix;
import com.intellij.openapi.projectRoots.impl.UnknownSdkTracker;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkResolver;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class UnknownSdkTrackerTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModuleCodeInsightFixture(PathManager.getHomePath());

  @Test
  public void testProjectSdk() {
    Sdk sdk = IdeaTestUtil.getMockJdk18();
    setProjectSdk2(sdk);
    ModuleRootModificationUtil.setSdkInherited(codeInsightFixture.get().getModule());

    final List<String> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .isEmpty();
  }

  @Test
  public void testMissingProjectJdk() {
    WriteAction.runAndWait(() -> {
      ProjectRootManager.getInstance(codeInsightFixture.get().getProject()).setProjectSdkName("missingSDK", JavaSdk.getInstance().getName());
    });

    final List<String> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .hasSize(1)
      .first().asString().startsWith("SdkFixInfo:");
  }

  @Test
  public void testMissingProjectUnknownJdk() {
    WriteAction.runAndWait(() -> {
      ProjectRootManager.getInstance(codeInsightFixture.get().getProject()).setProjectSdkName("missingSDK", "foo-bar-baz");
    });

    final List<String> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .isEmpty();
  }

  @Test
  public void testMissingModuleJdk() {
    setModuleSdk("missingSDK", JavaSdk.getInstance());

    List<String> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .hasSize(1)
      .first().asString().matches(s -> s.startsWith("SdkFixInfo:") || s.startsWith("SdkSetupNotification:"));
  }

  @Test
  public void testMissingModuleUnknownSdk() {
    setModuleSdk("missingSDK", "foo-bar-baz");

    List<String> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .hasSize(1)
      .first()
      .asString()
      .startsWith("SdkSetupNotification:");
  }

  @Test
  public void testNoProjectSdk() {
    setProjectSdk2(null);
    ModuleRootModificationUtil.setSdkInherited(codeInsightFixture.get().getModule());

    final List<String> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .isNotEmpty();
  }

  @Test
  public void testModuleSdk() {
    Sdk sdk = addSdkIfNeeded(IdeaTestUtil.getMockJdk18());
    ModuleRootModificationUtil.setModuleSdk(codeInsightFixture.get().getModule(), sdk);

    final List<String> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .isEmpty();
  }

  @Test
  public void testNoModuleSdk() {
    ModuleRootModificationUtil.setModuleSdk(codeInsightFixture.get().getModule(), null);
    final List<String> fixes = detectMissingSdks();
    assertThat(fixes)
      .withFailMessage(String.valueOf(fixes))
      .isNotEmpty();
  }

  @Test
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
    }, codeInsightFixture.get().getTestRootDisposable());

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

  @Test
  @TestFor(issues = "IDEA-237884")
  public void testShouldNotRantOnCustomSDKType() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final Sdk broken = ProjectJdkTable.getInstance().createSdk("broken-sdk-123", SimpleJavaSdkType.getInstance());
    WriteAction.runAndWait(() -> {
      SdkModificator m = broken.getSdkModificator();
      m.setHomePath("invalid home path");
      m.setVersionString("11");
      m.commitChanges();

      ProjectJdkTable.getInstance().addJdk(broken, myFixture.getTestRootDisposable());

      ModifiableRootModel mod = ModuleRootManager.getInstance(myFixture.getModule()).getModifiableModel();
      mod.setSdk(broken);
      mod.commit();
    });
    final @NotNull List<String> panel = detectMissingSdks();
    assertThat(panel).isEmpty();
  }

  private class SdkTestCases {
    final Sdk broken = ProjectJdkTable.getInstance().createSdk("broken-sdk-123", JavaSdk.getInstance());
    final Sdk jdkNotInJdkTable = ProjectJdkTable.getInstance().createSdk("not-in-jdk-table-123", JavaSdk.getInstance());
    final Sdk valid = IdeaTestUtil.getMockJdk18();

    private SdkTestCases() {
      WriteAction.runAndWait(() -> {
        SdkModificator m = broken.getSdkModificator();
        m.setHomePath("invalid home path");
        m.setVersionString("11");
        m.commitChanges();

        ProjectJdkTable.getInstance().addJdk(broken, codeInsightFixture.get().getTestRootDisposable());
        ProjectJdkTable.getInstance().addJdk(valid, codeInsightFixture.get().getTestRootDisposable());
      });
    }
  }

  @Test
  public void testBrokenModuleSdk() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    new SdkTestCases() {
      {
        WriteAction.runAndWait(() -> {
          ModifiableRootModel m = ModuleRootManager.getInstance(myFixture.getModule()).getModifiableModel();
          m.setSdk(broken);
          m.commit();

          ProjectRootManager.getInstance(myFixture.getProject()).setProjectSdk(valid);
        });

        final List<String> panel = detectMissingSdks();
        assertThat(panel).hasSize(1).first().asString().startsWith("SdkFixInfo:InvalidSdkFixInfo { name: " + broken.getName());
      }
    };
  }

  @Test
  public void testMissingModuleSdk() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    new SdkTestCases() {
      {
        WriteAction.runAndWait(() -> {
          ModifiableRootModel m = ModuleRootManager.getInstance(myFixture.getModule()).getModifiableModel();
          m.setSdk(jdkNotInJdkTable);
          m.commit();

          ProjectRootManager.getInstance(myFixture.getProject()).setProjectSdk(valid);
        });

        final List<String> panel = detectMissingSdks();
        assertThat(panel).hasSize(1).first().asString().startsWith("SdkFixInfo:SdkFixInfo {MissingSdkInfo(mySdkName=" + jdkNotInJdkTable.getName());
      }
    };
  }

  @Test
  public void testValidProjectSdk() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    new SdkTestCases() {
      {
        WriteAction.runAndWait(() -> {
          ModifiableRootModel m = ModuleRootManager.getInstance(myFixture.getModule()).getModifiableModel();
          m.inheritSdk();
          m.commit();

          ProjectRootManager.getInstance(myFixture.getProject()).setProjectSdk(valid);
        });

        final List<String> panel = detectMissingSdks();
        assertThat(panel).isEmpty();
      }
    };
  }

  @Test
  public void testBrokenProjectValidModuleSdk() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    new SdkTestCases() {
      {
        WriteAction.runAndWait(() -> {
          ModifiableRootModel m = ModuleRootManager.getInstance(myFixture.getModule()).getModifiableModel();
          m.setSdk(valid);
          m.commit();

          ProjectRootManager.getInstance(myFixture.getProject()).setProjectSdk(broken);
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
    WriteAction.runAndWait(() -> {
      ModifiableRootModel model = ModuleRootManager.getInstance(codeInsightFixture.get().getModule()).getModifiableModel();
      model.setInvalidSdk(sdkName, sdkType);
      model.commit();
    });
  }

  @BeforeEach
  void setUp() {
    setProjectSdk2(IdeaTestUtil.getMockJdk17());
  }

  private @NotNull List<String> detectMissingSdks() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    UnknownSdkTracker.getInstance(myFixture.getProject()).updateUnknownSdks();

    List<String> infos = new ArrayList<>();
    EditorNotificationPanel sdkNotification =
      EdtTestUtil.runInEdtAndGet(() -> SdkSetupNotificationTestBase.runOnText(myFixture, "Sample.java", "class Sample { java.lang.String foo; }"));
    if (sdkNotification != null) {
      infos.add("SdkSetupNotification:" + sdkNotification.getText());
    }

    for (UnknownSdkFix notification : UnknownSdkEditorNotification.getInstance(myFixture.getProject()).getNotifications()) {
      infos.add("SdkFixInfo:" + notification.toString());
    }

    return infos;
  }

  private void setProjectSdk2(@Nullable Sdk sdk) {
    WriteAction.runAndWait(() -> {
      ProjectRootManager.getInstance(codeInsightFixture.get().getProject()).setProjectSdk(addSdkIfNeeded(sdk));
    });
  }

  @Contract("null->null;!null->!null")
  private Sdk addSdkIfNeeded(Sdk sdk) {
    if (sdk == null) return null;

    ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();

    final Sdk foundJdk = jdkTable.findJdk(sdk.getName());
    if (foundJdk != null) return sdk;

    ThrowableRunnable<RuntimeException> addSdk = () -> {
      jdkTable.addJdk(sdk, codeInsightFixture.get().getProjectDisposable());
    };

    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      addSdk.run();
    } else {
      WriteAction.runAndWait(addSdk);
    }

    return sdk;
  }
}
