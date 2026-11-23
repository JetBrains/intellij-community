// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.java.codeInsight.daemon.JdkDaemonAnalyzerFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiRequiresStatement;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static com.intellij.java.codeInsight.daemon.JdkDaemonAnalyzerFixture.jdkDaemonAnalyzerFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class OrderEntryTest {
  @NonNls public static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/quickFix/orderEntry/";

  private final TestFixture<JdkDaemonAnalyzerFixture> daemonFixture = jdkDaemonAnalyzerFixture(
    JavaTestUtil.getJavaTestDataPath(), () -> IdeaTestUtil.getMockJdk9(), LanguageLevel.JDK_1_9,
    () -> LocalInspectionTool.EMPTY_ARRAY, _ -> JavaTestUtil.getJavaTestDataPath() + BASE_PATH);

  private Module myModule;

  @BeforeEach
  void setUp() {
    myModule = ModuleManager.getInstance(daemonFixture.get().getProject()).findModuleByName("A");
  }

  @AfterEach
  void tearDown() {
    removeLibs();
  }

  private void doTest(String fileName, boolean performAction) {
    JdkDaemonAnalyzerFixture fixture = daemonFixture.get();
    VirtualFile root = ReadAction.computeBlocking(() -> ModuleRootManager.getInstance(myModule).getContentRoots()[0].getParent());
    fixture.configureByExistingFile(root.findFileByRelativePath(fileName));
    VirtualFile virtualFile = fixture.getFile().getVirtualFile();
    ActionHint actionHint = ReadAction.computeBlocking(() -> ActionHint.parse(fixture.getFile(), fixture.getFile().getText()));
    Collection<HighlightInfo> infosBefore = fixture.highlightErrors();
    final IntentionAction action = findActionAndCheck(actionHint, infosBefore);

    if(action != null && performAction) {
      String text = action.getText();
      EdtTestUtil.runInEdtAndWait(() -> {
        WriteCommandAction.runWriteCommandAction(null, () -> action.invoke(fixture.getProject(), fixture.getEditor(), fixture.getFile()));
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion(); // error dialog shown in later
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      });
      fixture.setFile(ReadAction.computeBlocking(() -> PsiManager.getInstance(fixture.getProject()).findFile(virtualFile)));

      Collection<HighlightInfo> infosAfter = fixture.highlightErrors();
      final IntentionAction afterAction = findActionWithText(text);
      if (afterAction != null) {
        fail("Action '" + text + "' is still available after its invocation in test " + BASE_PATH + fileName);
      }
      assertTrue(infosBefore.size() > infosAfter.size());
    }
  }

  private IntentionAction findActionWithText(final String actionText) {
    JdkDaemonAnalyzerFixture fixture = daemonFixture.get();
    List<IntentionAction> actions = EdtTestUtil.runInEdtAndGet(() -> LightQuickFixTestCase.getAvailableActions(fixture.getEditor(), fixture.getFile()));
    return LightQuickFixTestCase.findActionWithText(actions, actionText);
  }

  private IntentionAction findActionAndCheck(final ActionHint actionHint, Collection<HighlightInfo> infosBefore) {
    JdkDaemonAnalyzerFixture fixture = daemonFixture.get();
    List<IntentionAction> actions = EdtTestUtil.runInEdtAndGet(() -> LightQuickFixTestCase.getAvailableActions(fixture.getEditor(), fixture.getFile()));
    return actionHint.findAndCheck(actions, () -> "Infos: " + infosBefore);
  }

  @Test
  public void testAddDependency() {
    removeModule("C");
    doTest("B/src/y/AddDependency.java", true);
    checkModuleInfo("B/src/module-info.java", "a");
  }

  @Test
  public void testAddAmbiguousDependency() {
    doTest("B/src/y/AddAmbiguous.java", true);
    checkModuleInfo("B/src/module-info.java", "a");
  }

  @Test
  public void testTestDependency() {
    doTest("C/src/z/B.java", true);
    checkModuleInfo("C/src/module-info.java", "b");
  }

  private void checkModuleInfo(@NotNull String moduleFileName, @NotNull String expectedModule) {
    Project project = daemonFixture.get().getProject();
    boolean found = ReadAction.computeBlocking(() -> {
      VirtualFile root = ModuleRootManager.getInstance(myModule).getContentRoots()[0].getParent();
      final VirtualFile moduleFile = root.findFileByRelativePath(moduleFileName);
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(moduleFile);
      PsiJavaModule module = (PsiJavaModule)SyntaxTraverser.psiTraverser().children(psiFile).filter(PsiJavaModule.class::isInstance).first();
      for (PsiRequiresStatement require : module.getRequires()) {
        if (require.getModuleName().equals(expectedModule)) return true;
      }
      return false;
    });
    if (found) return;
    fail("Expected module '" + expectedModule + "' not found");
  }

  private void removeModule(String name) {
    ModuleManager manager = ModuleManager.getInstance(daemonFixture.get().getProject());
    ModifiableModuleModel model = manager.getModifiableModel();
    model.disposeModule(manager.findModuleByName(name));
    WriteAction.runAndWait(() -> model.commit());
  }

  @Test
  public void testAddLibrary() {
    doTest("B/src/y/AddLibrary.java", true);
    checkModuleInfo("B/src/module-info.java", "lib");
  }

  @Test
  public void testAddCircularDependency() {
    final Module a = ModuleManager.getInstance(daemonFixture.get().getProject()).findModuleByName("A");
    final Module b = ModuleManager.getInstance(daemonFixture.get().getProject()).findModuleByName("B");
    ModuleRootModificationUtil.addDependency(a, b);
    removeModule("C");

    try {
      doTest("B/src/y/AddDependency.java", true);
      fail("user should have been warned");
    }
    catch (RuntimeException e) {
      final String expected = ReadAction.computeBlocking(
        () -> "Adding dependency on module '" + getModuleName(a) + "'" +
              " will introduce circular dependency between modules '" + getModuleName(a) + "' and '" +
              getModuleName(b) + "'.\n" + "Add dependency anyway?");
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

  @Test
  public void testAddJunit() {
    doTest("A/src/x/DoTest.java", false);
  }

  @Test
  public void testAddJunit4() {
    doTest("A/src/x/DoTest4.java", false);
  }

  @Test
  public void testAddJunit4inJunit() {
    doTest("A/src/x/DoTest4junit.java", false);
  }

  @Test
  public void testExistingJunit() {
    doTest("B/src/y/AddExistingJunit.java", true);
  }

  private void removeLibs() {
    WriteAction.runAndWait(() -> {
      try {
        for (Module module : ModuleManager.getInstance(daemonFixture.get().getProject()).getModules()) {
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
