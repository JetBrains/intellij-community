// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.roots;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.ide.projectView.impl.RenameModuleHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.ui.TestInputDialog;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.refactoring.rename.RenameModuleAndDirectoryHandler;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.HeavyTestHelper;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.createChildDirectory;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * @author Vladislav.Soroka
 */
@TestApplication
public class RenameModuleTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @AfterEach
  void tearDown() {
    TestDialogManager.setTestInputDialog(TestInputDialog.DEFAULT);
  }

  @Test
  public void testRename() {
    String moduleName = "module";
    String newModuleName = "moduleA";
    Module module = heavyFixture.get().createModule(moduleName);
    assertEquals(moduleName, module.getName());

    MapDataContext context = new MapDataContext();
    context.put(LangDataKeys.MODULE_CONTEXT, module);
    final RenameHandler renameHandler = ReadAction.computeBlocking(() -> RenameHandlerRegistry.getInstance().getRenameHandler(context));
    assertNotNull(renameHandler);
    rename(renameHandler, newModuleName, context);
    assertEquals(newModuleName, module.getName());
  }

  @Test
  public void testDontRenameDirectoryWhenRenamingModule() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module module = fixture.createModule("module");
    VirtualFile root = createChildDirectory(HeavyTestHelper.getOrCreateProjectBaseDir(fixture.getProject()), "module");
    PsiTestUtil.addContentRoot(module, root);
    PsiDirectory psiDirectory = ReadAction.computeBlocking(() -> PsiManager.getInstance(fixture.getProject()).findDirectory(root));
    MapDataContext context = new MapDataContext();
    context.put(LangDataKeys.MODULE_CONTEXT, module);
    context.put(CommonDataKeys.PSI_ELEMENT, psiDirectory);
    RenameHandler renameHandler = chooseHandler(context, RenameModuleHandler.class);
    assertNotNull(renameHandler);
    rename(renameHandler, "module2", context);
    assertEquals("module2", module.getName());
    assertEquals("module", psiDirectory.getName()); // directory remains unchanged
  }

  @Test
  public void testRenameModuleAndDirectory() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module module = fixture.createModule("module");
    VirtualFile root = createChildDirectory(HeavyTestHelper.getOrCreateProjectBaseDir(fixture.getProject()), "module");
    PsiTestUtil.addContentRoot(module, root);
    PsiDirectory directory = ReadAction.computeBlocking(() -> PsiManager.getInstance(fixture.getProject()).findDirectory(root));
    MapDataContext context = new MapDataContext();
    context.put(LangDataKeys.MODULE_CONTEXT, module);
    context.put(CommonDataKeys.PSI_ELEMENT, directory);
    context.put(PsiElementRenameHandler.DEFAULT_NAME, "newName");
    RenameHandler renameHandler = chooseHandler(context, RenameModuleAndDirectoryHandler.class);
    assumeTrue(
      renameHandler != null,
      "Other languages might have their own implementation of a processor that renames a directory. " +
      "In this case, the test fails under ultimate.tests.main."
    );
    EdtTestUtil.runInEdtAndWait(() -> renameHandler.invoke(fixture.getProject(), PsiElement.EMPTY_ARRAY, context));
    assertEquals("newName", module.getName());
    assertEquals("newName", directory.getName());
  }

  @Test
  public void testCannotRenameModuleAndDirectoryWhenNameDoesntMatch() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module module = fixture.createModule("module");
    VirtualFile root = createChildDirectory(HeavyTestHelper.getOrCreateProjectBaseDir(fixture.getProject()), "contentRoot");
    PsiTestUtil.addContentRoot(module, root);
    PsiDirectory directory = ReadAction.computeBlocking(() -> PsiManager.getInstance(fixture.getProject()).findDirectory(root));
    MapDataContext context = new MapDataContext();
    context.put(LangDataKeys.MODULE_CONTEXT, module);
    context.put(CommonDataKeys.PSI_ELEMENT, directory);
    assertNull(chooseHandler(context, RenameModuleAndDirectoryHandler.class));
  }

  @Nullable
  private RenameHandler chooseHandler(MapDataContext context, Class<? extends RenameHandler> handlerClass) {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    RenameHandlerRegistry registry = RenameHandlerRegistry.getInstance();
    Ref<Boolean> selectorWasUsed = Ref.create(false);
    registry.setRenameHandlerSelectorInTests(all -> {
      selectorWasUsed.set(true);
      return ContainerUtil.findInstance(all, handlerClass);
    }, fixture.getTestRootDisposable());
    RenameHandler renameHandler = ReadAction.computeBlocking(() -> registry.getRenameHandler(context));
    assertTrue(selectorWasUsed.get());
    return renameHandler;
  }

  private void rename(RenameHandler renameHandler, String newName, DataContext context) {
    TestDialogManager.setTestInputDialog(new TestInputDialog() {
      @Override
      public String show(String message) {
        return null;
      }

      @Override
      public String show(String message, @Nullable InputValidator validator) {
        assertNotNull(validator);
        boolean canClose = validator.canClose(newName);
        assertTrue(canClose);
        return newName;
      }
    });

    EdtTestUtil.runInEdtAndWait(() -> renameHandler.invoke(heavyFixture.get().getProject(), PsiElement.EMPTY_ARRAY, context));
  }
}