// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.SdkSetupNotificationProvider;
import com.intellij.codeInsight.intention.IntentionActionWithOptions;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationsImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * @author Pavel.Dolgov
 */
public abstract class SdkSetupNotificationTestBase extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    setProjectSdk(IdeaTestUtil.getMockJdk17());
  }

  @Nullable
  protected EditorNotificationPanel configureBySdkAndText(@Nullable Sdk sdk,
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

    final PsiFile psiFile = myFixture.configureByText(name, text);
    FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(getProject());
    VirtualFile virtualFile = psiFile.getVirtualFile();
    final FileEditor[] editors = fileEditorManager.openFile(virtualFile, true);
    Disposer.register(myFixture.getTestRootDisposable(), new Disposable() {
      @Override
      public void dispose() {
        fileEditorManager.closeFile(virtualFile);
      }
    });
    assertThat(editors).hasSize(1);
    EditorNotificationsImpl.completeAsyncTasks();

    return editors[0].getUserData(SdkSetupNotificationProvider.KEY);
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
