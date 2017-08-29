/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    final PsiFile psiFile = myFixture.configureByText(name, text);
    final FileEditor[] editors = FileEditorManagerEx.getInstanceEx(getProject()).openFile(psiFile.getVirtualFile(), true);
    assertSize(1, editors);

    if (moduleSdk) {
      ModuleRootModificationUtil.setModuleSdk(myModule, sdk);
    }
    else {
      setProjectSdk(sdk);
      ModuleRootModificationUtil.setSdkInherited(myModule);
    }
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
