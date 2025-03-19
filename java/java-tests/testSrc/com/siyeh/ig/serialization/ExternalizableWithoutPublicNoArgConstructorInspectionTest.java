// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.serialization;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class ExternalizableWithoutPublicNoArgConstructorInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/serialization/externalizable_without_public_no_arg_constructor";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  private void doTest() {
    myFixture.enableInspections(new ExternalizableWithoutPublicNoArgConstructorInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testExternalizableWithoutPublicNoArgConstructor() {
    doTest();
  }

  public void testPublicClass() {
    doTest();
  }
}
