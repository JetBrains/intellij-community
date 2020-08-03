// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.TestModuleProperties;
import com.intellij.testFramework.JavaModuleTestCase;

public class ModuleTestPropertiesTest extends JavaModuleTestCase {
  public void testSetAndGet() {
    Module tests = createModule("tests");
    TestModuleProperties moduleProperties = TestModuleProperties.getInstance(tests);
    moduleProperties.setProductionModuleName(myModule.getName());
    assertSame(myModule, moduleProperties.getProductionModule());
  }
}
