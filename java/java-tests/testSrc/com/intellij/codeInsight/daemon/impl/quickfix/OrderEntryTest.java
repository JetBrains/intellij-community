// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk9();
  }

  @Override
  protected @NotNull LanguageLevel getProjectLanguageLevel() {
    return LanguageLevel.JDK_1_9;
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
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion(); // error dialog shown in later
      UIUtil.dispatchAllInvocationEvents();
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
    removeModule("C");
    doTest("B/src/y/AddDependency.java", true);
    checkModuleInfo("B/src/module-info.java", "a");
  }

  public void testAddAmbiguousDependency() {
    doTest("B/src/y/AddAmbiguous.java", true);
    checkModuleInfo("B/src/module-info.java", "a");
  }

  public void testTestDependency() {
    doTest("C/src/z/B.java", true);
    checkModuleInfo("C/src/module-info.java", "b");
  }

  private void checkModuleInfo(@NotNull String moduleFileName, @NotNull String expectedModule) {
    VirtualFile root = ModuleRootManager.getInstance(myModule).getContentRoots()[0].getParent();
    final VirtualFile moduleFile = root.findFileByRelativePath(moduleFileName);
    final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(moduleFile);
    PsiJavaModule module = (PsiJavaModule)SyntaxTraverser.psiTraverser().children(psiFile).filter(PsiJavaModule.class::isInstance).first();
    for (PsiRequiresStatement require : module.getRequires()) {
      if (require.getModuleName().equals(expectedModule)) return;
    }
    fail("Expected module '" + expectedModule + "' not found");
  }

  private void removeModule(String name) {
    ModuleManager manager = ModuleManager.getInstance(getProject());
    ModifiableModuleModel model = manager.getModifiableModel();
    model.disposeModule(manager.findModuleByName(name));
    WriteAction.run(() -> model.commit());
  }

  public void testAddLibrary() {
    doTest("B/src/y/AddLibrary.java", true);
    checkModuleInfo("B/src/module-info.java", "lib");
  }

  public void testAddCircularDependency() {
    final Module a = ModuleManager.getInstance(getProject()).findModuleByName("A");
    final Module b = ModuleManager.getInstance(getProject()).findModuleByName("B");
    ModuleRootModificationUtil.addDependency(a, b);
    removeModule("C");

    try {
      doTest("B/src/y/AddDependency.java", true);
      fail("user should have been warned");
    }
    catch (RuntimeException e) {
      final String expected = "Adding dependency on module '" + getModuleName(a) + "'" +
                              " will introduce circular dependency between modules '" + getModuleName(a) + "' and '" +
                              getModuleName(b) + "'.\n" + "Add dependency anyway?";
      String message = e.getMessage();
      assertEquals(expected, message);
    }
  }

  @NotNull
  private static String getModuleName(@NotNull Module module) {
    final PsiJavaModule javaModule = JavaModuleGraphUtil.findDescriptorByModule(module, false);
    if (javaModule != null && PsiNameHelper.isValidModuleName(javaModule.getName(), javaModule)) {
      return javaModule.getName();
    } else {
      return module.getName();
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
