// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.roots;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.TestModuleProperties;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertSame;

@TestApplication
public class ModuleTestPropertiesTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @Test
  public void testSetAndGet() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module myModule = fixture.getModule();
    Module tests = fixture.createModule("tests");
    TestModuleProperties moduleProperties = TestModuleProperties.getInstance(tests);
    WriteAction.runAndWait(() -> {
      moduleProperties.setProductionModuleName(myModule.getName());
    });
    assertSame(myModule, moduleProperties.getProductionModule());
  }
}
