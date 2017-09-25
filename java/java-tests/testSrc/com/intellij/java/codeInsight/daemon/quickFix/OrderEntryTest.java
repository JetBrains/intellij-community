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
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.List;

/**
 * @author cdr
 */
public class OrderEntryTest extends DaemonAnalyzerTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/quickFix/orderEntry/";

  @Override
  protected void setUpProject() throws Exception {
    final String root = PathManagerEx.getTestDataPath() + BASE_PATH;

    VirtualFile tempProjectRootDir =
      PsiTestUtil.createTestProjectStructure(getTestName(true), null, FileUtil.toSystemIndependentName(root), myFilesToDelete, false);

    VirtualFile projectFile = tempProjectRootDir.findChild("orderEntry.ipr");

    myProject = ProjectManagerEx.getInstanceEx().loadProject(projectFile.getPath());
    ProjectManagerEx.getInstanceEx().openTestProject(myProject);
    UIUtil.dispatchAllInvocationEvents(); // startup activities

    setUpJdk();
    myModule = ModuleManager.getInstance(getProject()).getModules()[0];
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      removeLibs();
    }
    finally {
      super.tearDown();
    }
  }

  private void doTest(String fileName) {
    String testFullPath = BASE_PATH + fileName;

    VirtualFile root = ModuleRootManager.getInstance(myModule).getContentRoots()[0].getParent();
    VirtualFile virtualFile = root.findFileByRelativePath(fileName);
    configureByExistingFile(virtualFile);
    ActionHint actionHint = ActionHint.parse(getFile(), getFile().getText());
    Collection<HighlightInfo> infosBefore = highlightErrors();
    final IntentionAction action = findActionAndCheck(actionHint, infosBefore);

    if(action != null) {
      String text = action.getText();
      WriteCommandAction.runWriteCommandAction(null, () -> action.invoke(getProject(), getEditor(), getFile()));

      Collection<HighlightInfo> infosAfter = highlightErrors();
      final IntentionAction afterAction = findActionWithText(text);
      if (afterAction != null) {
        fail("Action '" + text + "' is still available after its invocation in test " + testFullPath);
      }
      assertEquals(infosBefore.size() - 1, infosAfter.size());
    }
  }

  private IntentionAction findActionWithText(final String actionText) {
    List<IntentionAction> actions = LightQuickFixTestCase.getAvailableActions(getEditor(), getFile());
    return LightQuickFixTestCase.findActionWithText(actions, actionText);
  }

  private IntentionAction findActionAndCheck(final ActionHint actionHint, Collection<HighlightInfo> infosBefore) {
    List<IntentionAction> actions = LightQuickFixTestCase.getAvailableActions(getEditor(), getFile());
    return actionHint.findAndCheck(actions, () -> "Infos: " + infosBefore);
  }

  public void testAddDependency() {
    doTest("B/src/y/AddDependency.java");
  }

  public void testAddLibrary() {
    doTest("B/src/y/AddLibrary.java");
  }

  public void testAddCircularDependency() {
    final Module a = ModuleManager.getInstance(getProject()).findModuleByName("A");
    final Module b = ModuleManager.getInstance(getProject()).findModuleByName("B");
    ModuleRootModificationUtil.addDependency(a, b);

    try {
      doTest("B/src/y/AddDependency.java");
      fail("user should have been warned");
    }
    catch (RuntimeException e) {
      final String expected = "Adding dependency on module '" + a.getName() + "'" +
                              " will introduce circular dependency between modules '" + a.getName() + "' and '" +
                              b.getName() + "'.\n" + "Add dependency anyway?";
      String message = e.getMessage();
      assertEquals(expected, message);
    }
  }

  public void testAddJunit() {
    doTest("A/src/x/DoTest.java");
  }

  public void testAddJunit4() {
    doTest("A/src/x/DoTest4.java");
  }

  public void testAddJunit4inJunit() {
    doTest("A/src/x/DoTest4junit.java");
  }

  public void testExistingJunit() {
    doTest("B/src/y/AddExistingJunit.java");
  }

  private void removeLibs() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        for (Module module : ModuleManager.getInstance(getProject()).getModules()) {
          ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
          ModifiableRootModel model = rootManager.getModifiableModel();
          for (OrderEntry orderEntry : model.getOrderEntries()) {
            model.removeOrderEntry(orderEntry);
          }
          model.commit();
        }
      }
      catch (Throwable e) {
        e.printStackTrace();  // when running test from within IDEA it would fail because junit.jar cache is locked by host IDEA instance
      }
    });
  }
}
