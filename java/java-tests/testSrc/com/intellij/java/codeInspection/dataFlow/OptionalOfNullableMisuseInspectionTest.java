// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.dataFlow;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.dataFlow.OptionalOfNullableMisuseInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class OptionalOfNullableMisuseInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/optionalOfNullable/";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  public void testOptionalOfNullableMisuse() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.enableInspections(new OptionalOfNullableMisuseInspection());
    myFixture.checkHighlighting();
  }
}
