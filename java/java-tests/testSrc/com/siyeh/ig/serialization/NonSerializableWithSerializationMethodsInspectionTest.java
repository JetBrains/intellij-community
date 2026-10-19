// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.serialization;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class NonSerializableWithSerializationMethodsInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/serialization/non_serializable_with_serialization_methods";
  }

  private void doTest() {
    myFixture.enableInspections(new NonSerializableWithSerializationMethodsInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testNonSerializableWithSerializationMethods() {
    doTest();
  }

}
