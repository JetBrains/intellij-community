// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.javadoc;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class DanglingJavadocInspectionTest extends LightJavaInspectionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9;
  }

  public void testDanglingJavadoc() {
    doTest();
  }

  public void testPackageInfo() {
    doNamedTest("package-info");
  }

  public void testModuleInfo() {
    doNamedTest("module-info");
  }

  public void testTemplate() {
    myFixture.configureByFile("App.java.ft");
    myFixture.testHighlighting(true, false, false);
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new DanglingJavadocInspection();
  }
}