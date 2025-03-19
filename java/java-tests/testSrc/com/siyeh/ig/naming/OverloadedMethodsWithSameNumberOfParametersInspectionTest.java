// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.naming;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class OverloadedMethodsWithSameNumberOfParametersInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/naming/overloaded_methods_with_same_number_of_parameters";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void testIgnoreInconvertibleTypes() {
    myFixture.enableInspections(new OverloadedMethodsWithSameNumberOfParametersInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testReportAll() {
    OverloadedMethodsWithSameNumberOfParametersInspection inspection = new OverloadedMethodsWithSameNumberOfParametersInspection();
    inspection.ignoreInconvertibleTypes = false;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

}
