// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.SdkSetupNotificationProvider;
import com.intellij.codeInsight.intention.IntentionActionWithOptions;
import com.intellij.idea.TestFor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationsImpl;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * @author Pavel.Dolgov
 */

@TestFor(classes = SdkSetupNotificationProvider.class)
public abstract class SdkSetupNotificationTestBase extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    setProjectSdk(IdeaTestUtil.getMockJdk17());
  }

  protected @Nullable EditorNotificationPanel configureBySdkAndText(@Nullable Sdk sdk,
                                                                    boolean isModuleSdk,
                                                                    @NotNull String name,
                                                                    @NotNull String text) {
    if (isModuleSdk) {
      ModuleRootModificationUtil.setModuleSdk(getModule(), sdk);
    }
    else {
      setProjectSdk(sdk);
      ModuleRootModificationUtil.setSdkInherited(getModule());
    }

    return runOnText(myFixture, name, text);
  }

  static @Nullable EditorNotificationPanel runOnText(@NotNull JavaCodeInsightTestFixture fixture,
                                                     @NotNull String fileName,
                                                     @NotNull String fileText) {
    FileEditor editor = openTextInEditor(fixture, fileName, fileText);
    return (EditorNotificationPanel)EditorNotificationsImpl.getNotificationPanels(editor)
      .get(SdkSetupNotificationProvider.class);
  }

  static @NotNull FileEditor openTextInEditor(@NotNull JavaCodeInsightTestFixture fixture,
                                              @NotNull String fileName,
                                              @NotNull String fileText) {
    UIUtil.dispatchAllInvocationEvents();
    EditorNotificationsImpl.completeAsyncTasks();

    final PsiFile psiFile = fixture.configureByText(fileName, fileText);
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(fixture.getProject());
    VirtualFile virtualFile = psiFile.getVirtualFile();

    final FileEditor[] editors = fileEditorManager.openFile(virtualFile, true);
    Disposer.register(fixture.getTestRootDisposable(), new Disposable() {
      @Override
      public void dispose() {
        fileEditorManager.closeFile(virtualFile);
      }
    });
    assertThat(editors).hasSize(1);

    UIUtil.dispatchAllInvocationEvents();
    EditorNotificationsImpl.completeAsyncTasks();

    return editors[0];
  }

  protected void setProjectSdk(@Nullable Sdk sdk) {
    if (sdk != null) {
      final Sdk foundJdk = ReadAction.compute(() -> ProjectJdkTable.getInstance().findJdk(sdk.getName()));
      if (foundJdk == null) {
        WriteAction.run(() -> ProjectJdkTable.getInstance().addJdk(sdk, myFixture.getProjectDisposable()));
      }
    }
    WriteAction.run(() -> ProjectRootManager.getInstance(getProject()).setProjectSdk(sdk));
  }

  protected static void assertSdkSetupPanelShown(EditorNotificationPanel panel, @NotNull String expectedMessagePrefix) {
    assertThat(panel).isNotNull();
    final IntentionActionWithOptions action = panel.getIntentionAction();
    assertThat(action).isNotNull();
    final String text = action.getText();
    assertThat(text).isNotNull();
    if (!text.startsWith(expectedMessagePrefix)) {
      final int length = Math.min(text.length(), expectedMessagePrefix.length());
      assertThat(text.substring(0, length)).isEqualTo(expectedMessagePrefix);
    }
  }
}
