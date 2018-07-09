// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.PlatformTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    createModule("a");
    assertNotNull(getModuleManager().findModuleByName("a"));
    deleteModules("a");
    assertNull(getModuleManager().findModuleByName("a"));
  }

  public void testDeleteDependency() {
    Module a = createModule("a");
    Module b = createModule("b");
    ModuleRootModificationUtil.addDependency(a, b);
    assertSameElements(ModuleRootManager.getInstance(a).getDependencyModuleNames(), "b");
    deleteModules("b");
    assertEmpty(ModuleRootManager.getInstance(a).getDependencyModuleNames());
  }

  public void testDeleteTwoModules() {
    Module a = createModule("a");
    Module b = createModule("b");
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(myModule, a);
    ModuleRootModificationUtil.addDependency(myModule, b);
    assertSameElements(ModuleRootManager.getInstance(myModule).getDependencyModuleNames(), "a", "b");
    deleteModules("a", "b");
    assertNull(getModuleManager().findModuleByName("a"));
    assertNull(getModuleManager().findModuleByName("b"));
    assertEmpty(ModuleRootManager.getInstance(myModule).getDependencyModuleNames());
  }

  public void testUnloaded() {
    createModule("a");
    getModuleManager().setUnloadedModules(Arrays.asList("a"));
    assertNotNull(getModuleManager().getUnloadedModuleDescription("a"));
    deleteModules("a");
    assertNull(getModuleManager().getUnloadedModuleDescription("a"));
  }

  public void testDeleteDependencyOnUnloadedModule() {
    Module a = createModule("a");
    Module b = createModule("b");
    ModuleRootModificationUtil.addDependency(a, b);
    getModuleManager().setUnloadedModules(Arrays.asList("b"));
    assertSameElements(ModuleRootManager.getInstance(a).getDependencyModuleNames(), "b");
    deleteModules("b");
    assertEmpty(ModuleRootManager.getInstance(a).getDependencyModuleNames());
  }

  public void testLoadedAndUnloadedModule() {
    Module a = createModule("a");
    Module b = createModule("b");
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(myModule, a);
    ModuleRootModificationUtil.addDependency(myModule, b);
    getModuleManager().setUnloadedModules(Arrays.asList("a"));
    assertSameElements(ModuleRootManager.getInstance(myModule).getDependencyModuleNames(), "a", "b");
    deleteModules("a", "b");
    assertNull(getModuleManager().findModuleByName("a"));
    assertNull(getModuleManager().findModuleByName("b"));
    assertEmpty(getModuleManager().getUnloadedModuleDescriptions());
    assertEmpty(ModuleRootManager.getInstance(myModule).getDependencyModuleNames());
  }

  private ModuleManager getModuleManager() {
    return ModuleManager.getInstance(myProject);
  }

  private void deleteModules(String... names) {
    ModuleDeleteProvider provider = new ModuleDeleteProvider();
    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, myProject);
    List<Module> modules = new ArrayList<>();
    List<UnloadedModuleDescription> unloaded = new ArrayList<>();
    for (String name : names) {
      UnloadedModuleDescription description = getModuleManager().getUnloadedModuleDescription(name);
      if (description != null) {
        unloaded.add(description);
      }
      else {
        Module module = getModuleManager().findModuleByName(name);
        assertNotNull("Module " + name + " not found", module);
        modules.add(module);
      }
    }
    if (!modules.isEmpty()) {
      dataContext.put(LangDataKeys.MODULE_CONTEXT_ARRAY, modules.toArray(Module.EMPTY_ARRAY));
    }
    if (!unloaded.isEmpty()) {
      dataContext.put(ProjectView.UNLOADED_MODULES_CONTEXT_KEY, unloaded);
    }
    assertTrue(provider.canDeleteElement(dataContext));
    provider.deleteElement(dataContext);
  }

  @Override
  public void tearDown() throws Exception {
    Messages.setTestDialog(TestDialog.DEFAULT);
    super.tearDown();
  }
}
