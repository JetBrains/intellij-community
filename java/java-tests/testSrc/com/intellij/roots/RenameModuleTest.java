/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.roots;

import com.intellij.ide.projectView.impl.RenameModuleHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestInputDialog;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.ModuleTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 5/26/2016
 */
public class RenameModuleTest extends ModuleTestCase {

  @Override
  protected void tearDown() throws Exception {
    try {
      Messages.setTestInputDialog(TestInputDialog.DEFAULT);
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

  public void testRenameModuleByRenamingContentRoot() {
    Module module = createModule("module");
    VirtualFile root = createChildDirectory(getOrCreateProjectBaseDir(), "module");
    PsiTestUtil.addContentRoot(module, root);
    PsiDirectory psiDirectory = PsiManager.getInstance(myProject).findDirectory(root);
    MapDataContext context = new MapDataContext();
    context.put(LangDataKeys.MODULE_CONTEXT, module);
    context.put(CommonDataKeys.PSI_ELEMENT, psiDirectory);
    RenameHandler renameHandler = chooseHandler(context, RenameModuleHandler.class);
    rename(renameHandler, "module2", context);
    assertEquals("module2", module.getName());
  }

  private RenameHandler chooseHandler(MapDataContext context, Class<? extends RenameHandler> handlerClass) {
    RenameHandlerRegistry registry = RenameHandlerRegistry.getInstance();
    Ref<Boolean> selectorWasUsed = Ref.create(false);
    registry.setRenameHandlerSelectorInTests(all -> {
      selectorWasUsed.set(true);
      assertEquals(2, all.size());
      return ContainerUtil.findInstance(all, handlerClass);
    }, getTestRootDisposable());
    RenameHandler renameHandler = registry.getRenameHandler(context);
    assertTrue(selectorWasUsed.get());
    assertNotNull(renameHandler);
    return renameHandler;
  }

  private void rename(RenameHandler renameHandler, String newName, DataContext context) {
    Messages.setTestInputDialog(new TestInputDialog() {
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
