// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.openapi.roots.ui.configuration.actions;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ModuleDeleteProviderTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @BeforeEach
  void setUp() {
    TestDialogManager.setTestDialog(TestDialog.OK);
  }

  @Test
  public void testSimple() {
    heavyFixture.get().createModule("a");
    assertNotNull(getModuleManager().findModuleByName("a"));
    deleteModules("a");
    assertNull(getModuleManager().findModuleByName("a"));
  }

  @Test
  public void testDeleteDependency() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module a = fixture.createModule("a");
    Module b = fixture.createModule("b");
    ModuleRootModificationUtil.addDependency(a, b);
    assertThat(ModuleRootManager.getInstance(a).getDependencyModuleNames()).containsExactlyInAnyOrder("b");
    deleteModules("b");
    assertThat(ModuleRootManager.getInstance(a).getDependencyModuleNames()).isEmpty();
  }

  @Test
  public void testDeleteTwoModules() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module a = fixture.createModule("a");
    Module b = fixture.createModule("b");
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(fixture.getModule(), a);
    ModuleRootModificationUtil.addDependency(fixture.getModule(), b);
    assertThat(ModuleRootManager.getInstance(fixture.getModule()).getDependencyModuleNames()).containsExactlyInAnyOrder("a", "b");
    deleteModules("a", "b");
    assertNull(getModuleManager().findModuleByName("a"));
    assertNull(getModuleManager().findModuleByName("b"));
    assertThat(ModuleRootManager.getInstance(fixture.getModule()).getDependencyModuleNames()).isEmpty();
  }

  @Test
  public void testUnloaded() {
    heavyFixture.get().createModule("a");
    getModuleManager().setUnloadedModulesSync(List.of("a"));
    assertNotNull(getModuleManager().getUnloadedModuleDescription("a"));
    deleteModules("a");
    assertNull(getModuleManager().getUnloadedModuleDescription("a"));
  }

  @Test
  public void testDeleteDependencyOnUnloadedModule() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module a = fixture.createModule("a");
    Module b = fixture.createModule("b");
    ModuleRootModificationUtil.addDependency(a, b);
    getModuleManager().setUnloadedModulesSync(List.of("b"));
    assertThat(ModuleRootManager.getInstance(a).getDependencyModuleNames()).containsExactlyInAnyOrder("b");
    deleteModules("b");
    assertThat(ModuleRootManager.getInstance(a).getDependencyModuleNames()).isEmpty();
  }

  @Test
  public void testLoadedAndUnloadedModule() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module a = fixture.createModule("a");
    Module b = fixture.createModule("b");
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(fixture.getModule(), a);
    ModuleRootModificationUtil.addDependency(fixture.getModule(), b);
    getModuleManager().setUnloadedModulesSync(List.of("a"));
    assertThat(ModuleRootManager.getInstance(fixture.getModule()).getDependencyModuleNames()).containsExactlyInAnyOrder("a", "b");
    deleteModules("a", "b");
    assertNull(getModuleManager().findModuleByName("a"));
    assertNull(getModuleManager().findModuleByName("b"));
    assertThat(getModuleManager().getUnloadedModuleDescriptions()).isEmpty();
    assertThat(ModuleRootManager.getInstance(fixture.getModule()).getDependencyModuleNames()).isEmpty();
  }

  private ModuleManager getModuleManager() {
    return ModuleManager.getInstance(heavyFixture.get().getProject());
  }

  private void deleteModules(String... names) {
    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, heavyFixture.get().getProject());
    List<Module> modules = new ArrayList<>();
    List<UnloadedModuleDescription> unloaded = new ArrayList<>();
    for (String name : names) {
      UnloadedModuleDescription description = getModuleManager().getUnloadedModuleDescription(name);
      if (description != null) {
        unloaded.add(description);
      }
      else {
        Module module = getModuleManager().findModuleByName(name);
        assertNotNull(module, "Module " + name + " not found");
        modules.add(module);
      }
    }
    if (!modules.isEmpty()) {
      dataContext.put(LangDataKeys.MODULE_CONTEXT_ARRAY, modules.toArray(Module.EMPTY_ARRAY));
    }
    if (!unloaded.isEmpty()) {
      dataContext.put(ProjectView.UNLOADED_MODULES_CONTEXT_KEY, unloaded);
    }
    ModuleDeleteProvider provider = ModuleDeleteProvider.getInstance();
    EdtTestUtil.runInEdtAndWait(() -> {
      assertTrue(provider.canDeleteElement(dataContext));
      provider.deleteElement(dataContext);
    });
  }

  @AfterEach
  void tearDown() {
    TestDialogManager.setTestDialog(TestDialog.DEFAULT);
  }
}