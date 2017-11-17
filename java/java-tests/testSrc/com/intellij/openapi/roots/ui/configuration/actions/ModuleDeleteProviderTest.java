// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.PlatformTestCase;

/**
 * @author nik
 */
public class ModuleDeleteProviderTest extends PlatformTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    Messages.setTestDialog(TestDialog.OK);
  }

  public void testSimple() {
    Module a = createModule("a");
    assertNotNull(ModuleManager.getInstance(myProject).findModuleByName("a"));
    deleteModules(a);
    assertNull(ModuleManager.getInstance(myProject).findModuleByName("a"));
  }

  public void testDeleteDependency() {
    Module a = createModule("a");
    Module b = createModule("b");
    ModuleRootModificationUtil.addDependency(a, b);
    assertSameElements(ModuleRootManager.getInstance(a).getDependencyModuleNames(), "b");
    deleteModules(b);
    assertEmpty(ModuleRootManager.getInstance(a).getDependencyModuleNames());
  }

  public void testDeleteTwoModules() {
    Module a = createModule("a");
    Module b = createModule("b");
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(myModule, a);
    ModuleRootModificationUtil.addDependency(myModule, b);
    assertSameElements(ModuleRootManager.getInstance(myModule).getDependencyModuleNames(), "a", "b");
    deleteModules(a, b);
    assertNull(ModuleManager.getInstance(myProject).findModuleByName("a"));
    assertNull(ModuleManager.getInstance(myProject).findModuleByName("b"));
    assertEmpty(ModuleRootManager.getInstance(myModule).getDependencyModuleNames());
  }

  private void deleteModules(Module... modules) {
    ModuleDeleteProvider provider = new ModuleDeleteProvider();
    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, myProject);
    dataContext.put(LangDataKeys.MODULE_CONTEXT_ARRAY, modules);
    assertTrue(provider.canDeleteElement(dataContext));
    provider.deleteElement(dataContext);
  }

  @Override
  public void tearDown() throws Exception {
    Messages.setTestDialog(TestDialog.DEFAULT);
    super.tearDown();
  }
}
