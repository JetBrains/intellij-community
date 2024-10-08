// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class DataFlowInspection9Test extends DataFlowInspectionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9_ANNOTATED;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }

  public void testMutabilityJdk9() { doTest();}
  public void testMutabilityInferred() { doTest(); }
  public void testObjectsRequireNonNullElse() { doTest(); }
  public void testNewCollectionAliasing() { doTest(); }

  public void testOptionalStreamInlining() { doTest(); }

  public void testNullabilityAnnotationOnModule() {
    addJSpecifyNullMarked(myFixture);
    myFixture.addFileToProject("module-info.java", """
      import org.jspecify.annotations.NullMarked;

      @NullMarked
      module jspecifysample {
      	requires org.jspecify;
      }""");
    doTest();
  }

  public void testJSpecifyNullMarkedLocals() {
    addJSpecifyNullMarked(myFixture);
    doTest();
  }

  public void testJSpecifyUpperBound() {
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }
}