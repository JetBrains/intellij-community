// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import org.jetbrains.annotations.NotNullByDefault;

@NotNullByDefault
public class OverrideImplementNullabilityTest extends OverrideImplementBaseTest {
  @Override
  protected String getBaseDir() {
    return "/codeInsight/overrideImplement/nullability/";
  }

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new SimpleLightProjectDescriptor(getModuleTypeId(), getProjectJDK()) {
      @Override
      protected void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
        DefaultLightProjectDescriptor.addJetBrainsAnnotationsWithTypeUse(model);
        MavenDependencyUtil.addFromMaven(model, "org.jspecify:jspecify:1.0.0");
      }
    };
  }

  public void testUnspecifiedSuperNullMarkedSubclassNormal() {
    doTest();
  }

  public void testUnspecifiedSuperNullMarkedSubclassArray() {
    doTest();
  }

  public void testUnspecifiedSuperNullMarkedSubclassVarargs() {
    doTest();
  }

  public void testUnspecifiedSuperNullMarkedSubclassGeneric() {
    doTest();
  }

  protected void doTest() { doTest(false, true); }
}