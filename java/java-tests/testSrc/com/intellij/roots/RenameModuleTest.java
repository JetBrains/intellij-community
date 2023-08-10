// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.roots;

import com.intellij.ide.projectView.impl.RenameModuleHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
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
import com.intellij.testFramework.JUnit38AssumeSupportRunner;
import com.intellij.testFramework.JavaModuleTestCase;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.junit.Assume;
import org.junit.runner.RunWith;

/**
 * @author Vladislav.Soroka
 */
@RunWith(JUnit38AssumeSupportRunner.class)
public class RenameModuleTest extends JavaModuleTestCase {
  @Override
  protected void tearDown() throws Exception {
    try {
      TestDialogManager.setTestInputDialog(TestInputDialog.DEFAULT);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testRename() {
    String moduleName = "module";
    String newModuleName = "moduleA";
    Module module = createModule(moduleName);
    assertEquals(moduleName, module.getName());

    MapDataContext context = new MapDataContext();
    context.put(LangDataKeys.MODULE_CONTEXT, module);
    final RenameHandler renameHandler = RenameHandlerRegistry.getInstance().getRenameHandler(context);
    assertNotNull(renameHandler);
    rename(renameHandler, newModuleName, context);
    assertEquals(newModuleName, module.getName());
  }

  public void testDontRenameDirectoryWhenRenamingModule() {
    Module module = createModule("module");
    VirtualFile root = createChildDirectory(getOrCreateProjectBaseDir(), "module");
    PsiTestUtil.addContentRoot(module, root);
    PsiDirectory psiDirectory = PsiManager.getInstance(myProject).findDirectory(root);
    MapDataContext context = new MapDataContext();
    context.put(LangDataKeys.MODULE_CONTEXT, module);
    context.put(CommonDataKeys.PSI_ELEMENT, psiDirectory);
    RenameHandler renameHandler = chooseHandler(context, RenameModuleHandler.class);
    assertNotNull(renameHandler);
    rename(renameHandler, "module2", context);
    assertEquals("module2", module.getName());
    assertEquals("module", psiDirectory.getName()); // directory remains unchanged
  }

  public void testRenameModuleAndDirectory() {
    Module module = createModule("module");
    VirtualFile root = createChildDirectory(getOrCreateProjectBaseDir(), "module");
    PsiTestUtil.addContentRoot(module, root);
    PsiDirectory directory = PsiManager.getInstance(myProject).findDirectory(root);
    MapDataContext context = new MapDataContext();
    context.put(LangDataKeys.MODULE_CONTEXT, module);
    context.put(CommonDataKeys.PSI_ELEMENT, directory);
    context.put(PsiElementRenameHandler.DEFAULT_NAME, "newName");
    RenameHandler renameHandler = chooseHandler(context, RenameModuleAndDirectoryHandler.class);
    Assume.assumeNotNull(
      "Other languages might have their own implementation of a processor that renames a directory. " +
      "In this case, the test fails under ultimate.tests.main.", 
      renameHandler
    );
    renameHandler.invoke(myProject, PsiElement.EMPTY_ARRAY, context);
    assertEquals("newName", module.getName());
    assertEquals("newName", directory.getName());
  }

  public void testCannotRenameModuleAndDirectoryWhenNameDoesntMatch() {
    Module module = createModule("module");
    VirtualFile root = createChildDirectory(getOrCreateProjectBaseDir(), "contentRoot");
    PsiTestUtil.addContentRoot(module, root);
    PsiDirectory directory = PsiManager.getInstance(myProject).findDirectory(root);
    MapDataContext context = new MapDataContext();
    context.put(LangDataKeys.MODULE_CONTEXT, module);
    context.put(CommonDataKeys.PSI_ELEMENT, directory);
    assertNull(chooseHandler(context, RenameModuleAndDirectoryHandler.class));
  }

  @Nullable
  private RenameHandler chooseHandler(MapDataContext context, Class<? extends RenameHandler> handlerClass) {
    RenameHandlerRegistry registry = RenameHandlerRegistry.getInstance();
    Ref<Boolean> selectorWasUsed = Ref.create(false);
    registry.setRenameHandlerSelectorInTests(all -> {
      selectorWasUsed.set(true);
      return ContainerUtil.findInstance(all, handlerClass);
    }, getTestRootDisposable());
    RenameHandler renameHandler = registry.getRenameHandler(context);
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

    renameHandler.invoke(myProject, PsiElement.EMPTY_ARRAY, context);
  }
}