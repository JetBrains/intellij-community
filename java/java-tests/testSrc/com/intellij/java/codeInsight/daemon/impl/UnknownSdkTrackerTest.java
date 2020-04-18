// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.idea.TestFor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.projectRoots.impl.UnknownSdkEditorNotification;
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
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.java.codeInsight.daemon.impl.SdkSetupNotificationTestBase.openTextInEditor;
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

    assertThat(unknownSdkNotificationsFor("Sample.java", "class Sample { java.lang.String foo; }")).hasSize(1);
    assertThat(unknownSdkNotificationsFor("jonnyzzz.js", "(function() { })();")).isEmpty();
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
    UnknownSdkResolver.EP_NAME.getPoint(null).registerExtension(new UnknownSdkResolver() {
      @Override
      public boolean supportsResolution(@NotNull SdkTypeId sdkTypeId) {
        return true;
      }

      @Nullable
      @Override
      public UnknownSdkLookup createResolver(@Nullable Project project, @NotNull ProgressIndicator indicator) {
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

  @Nullable
  private List<EditorNotificationPanel> unknownSdkNotificationsFor(@NotNull String fileName, @NotNull String text) {
    FileEditor editor = openTextInEditor(myFixture, fileName, text);

    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    UIUtil.dispatchAllInvocationEvents();

    return editor.getUserData(UnknownSdkEditorNotification.NOTIFICATIONS);
  }

  @NotNull
  private List<String> detectMissingSdks() {
    UnknownSdkTracker.getInstance(getProject()).updateUnknownSdksNow();

    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    UIUtil.dispatchAllInvocationEvents();

    ArrayList<String> infos = new ArrayList<>();
    for (UnknownSdkEditorNotification.SimpleSdkFixInfo notification : UnknownSdkEditorNotification.getInstance(getProject()).getNotifications()) {
      infos.add("SdkFixInfo:" + notification.toString());
    }

    EditorNotificationPanel sdkNotification = SdkSetupNotificationTestBase.runOnText(myFixture, "Sample.java", "class Sample { java.lang.String foo; }");
    if (sdkNotification != null) {
      infos.add("SdkSetupNotification:" + sdkNotification.getIntentionAction().getText());
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
