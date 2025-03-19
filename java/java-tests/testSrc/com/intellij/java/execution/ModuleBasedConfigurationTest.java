// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution;

import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import org.jetbrains.annotations.NotNull;

public class ModuleBasedConfigurationTest extends BaseConfigurationTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    addModule("module1");
    addModule("module2");
    addModule("module3");
  }

  public void testOriginalModule() {
    ModuleRootModificationUtil.addDependency(getModule1(), getModule2(), DependencyScope.TEST, true);
    ModuleRootModificationUtil.addDependency(getModule2(), getModule3(), DependencyScope.TEST, false);
    assertTrue(ModuleBasedConfiguration.canRestoreOriginalModule(getModule1(), new com.intellij.openapi.module.Module[] {getModule2()}));
    assertTrue(ModuleBasedConfiguration.canRestoreOriginalModule(getModule1(), new com.intellij.openapi.module.Module[] {getModule3()}));

    //not exported but on the classpath
    addModule("module4");
    ModuleRootModificationUtil.addDependency(getModule3(), getModule4(), DependencyScope.TEST, false);
    assertTrue(ModuleBasedConfiguration.canRestoreOriginalModule(getModule1(), new com.intellij.openapi.module.Module[] {getModule4()}));

    addModule("module5");
    assertFalse(ModuleBasedConfiguration.canRestoreOriginalModule(getModule1(), new com.intellij.openapi.module.Module[] {getModule(4)}));

    assertFalse(ModuleBasedConfiguration.canRestoreOriginalModule(getModule2(), new Module[] {getModule1()}));
  }

  @Override
  @NotNull
  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }
}
