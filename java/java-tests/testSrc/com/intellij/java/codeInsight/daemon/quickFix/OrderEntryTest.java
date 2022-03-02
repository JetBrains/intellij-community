// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

public class OrderEntryTest extends DaemonAnalyzerTestCase {
  @NonNls public static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/quickFix/orderEntry/";

  @Override
  protected void setUpProject() throws Exception {
    final String root = PathManagerEx.getTestDataPath() + BASE_PATH;

    VirtualFile tempProjectRootDir = createTestProjectStructure(null, FileUtil.toSystemIndependentName(root), false, getTempDir());

    VirtualFile projectFile = tempProjectRootDir.findChild("orderEntry.ipr");

    myProject = PlatformTestUtil.loadAndOpenProject(Paths.get(projectFile.getPath()), getTestRootDisposable());
    UIUtil.dispatchAllInvocationEvents(); // startup activities

    setUpJdk();
    myModule = ModuleManager.getInstance(getProject()).findModuleByName("A");
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      removeLibs();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private void doTest(String fileName, boolean performAction) {
    VirtualFile root = ModuleRootManager.getInstance(myModule).getContentRoots()[0].getParent();
    configureByExistingFile(root.findFileByRelativePath(fileName));
    VirtualFile virtualFile = getFile().getVirtualFile();
    ActionHint actionHint = ActionHint.parse(getFile(), getFile().getText());
    Collection<HighlightInfo> infosBefore = highlightErrors();
    final IntentionAction action = findActionAndCheck(actionHint, infosBefore);

    if(action != null && performAction) {
      String text = action.getText();
      WriteCommandAction.runWriteCommandAction(null, () -> action.invoke(getProject(), getEditor(), getFile()));

      myFile = getPsiManager().findFile(virtualFile);

      Collection<HighlightInfo> infosAfter = highlightErrors();
      final IntentionAction afterAction = findActionWithText(text);
      if (afterAction != null) {
        fail("Action '" + text + "' is still available after its invocation in test " + BASE_PATH + fileName);
      }
      assertTrue(infosBefore.size() > infosAfter.size());
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
    removeModule();
    doTest("B/src/y/AddDependency.java", true);
  }

  public void testAddAmbiguousDependency() {
    doTest("B/src/y/AddAmbiguous.java", true);
  }

  public void testTestDependency() {
    doTest("C/src/z/B.java", true);
  }

  private void removeModule() {
    ModuleManager manager = ModuleManager.getInstance(getProject());
    ModifiableModuleModel model = manager.getModifiableModel();
    model.disposeModule(manager.findModuleByName("C"));
    WriteAction.run(() -> model.commit());
  }

  public void testAddLibrary() {
    doTest("B/src/y/AddLibrary.java", true);
  }

  public void testAddCircularDependency() {
    final Module a = ModuleManager.getInstance(getProject()).findModuleByName("A");
    final Module b = ModuleManager.getInstance(getProject()).findModuleByName("B");
    ModuleRootModificationUtil.addDependency(a, b);
    removeModule();

    try {
      doTest("B/src/y/AddDependency.java", true);
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
    doTest("A/src/x/DoTest.java", false);
  }

  public void testAddJunit4() {
    doTest("A/src/x/DoTest4.java", false);
  }

  public void testAddJunit4inJunit() {
    doTest("A/src/x/DoTest4junit.java", false);
  }

  public void testAddCheckerFrameworkTainted() {
    doTest("A/src/x/AddCheckerFrameworkTainted.java", false);
  }

  public void testAddCheckerFrameworkUntainted() {
    doTest("A/src/x/AddCheckerFrameworkUntainted.java", false);
  }

  public void testExistingJunit() {
    doTest("B/src/y/AddExistingJunit.java", true);
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
