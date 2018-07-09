// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.SdkSetupNotificationProvider;
import com.intellij.codeInsight.intention.IntentionActionWithOptions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
public abstract class SdkSetupNotificationTestBase extends JavaCodeInsightFixtureTestCase {

  protected void setUp() throws Exception {
    super.setUp();

    setProjectSdk(IdeaTestUtil.getMockJdk17());
    new SdkSetupNotificationProvider(getProject(), EditorNotifications.getInstance(getProject()));
  }

  @Override
  protected void tearDown() throws Exception {
    FileEditorManagerEx.getInstanceEx(getProject()).closeAllFiles();
    super.tearDown();
  }

  protected EditorNotificationPanel configureBySdkAndText(@Nullable Sdk sdk,
                                                          boolean moduleSdk,
                                                          @NotNull String name,
                                                          @NotNull String text) {
    if (moduleSdk) {
      ModuleRootModificationUtil.setModuleSdk(myModule, sdk);
    }
    else {
      setProjectSdk(sdk);
      ModuleRootModificationUtil.setSdkInherited(myModule);
    }

    final PsiFile psiFile = myFixture.configureByText(name, text);
    final FileEditor[] editors = FileEditorManagerEx.getInstanceEx(getProject()).openFile(psiFile.getVirtualFile(), true);
    assertSize(1, editors);

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

  protected static void assertSdkSetupPanelShown(EditorNotificationPanel panel, String expectedMessagePrefix) {
    assertNotNull(panel);
    final IntentionActionWithOptions action = panel.getIntentionAction();
    assertNotNull(action);
    final String text = action.getText();
    assertNotNull(text);
    if (!text.startsWith(expectedMessagePrefix)) {
      final int length = Math.min(text.length(), expectedMessagePrefix.length());
      assertEquals(expectedMessagePrefix, text.substring(0, length));
    }
  }
}
