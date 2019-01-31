/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class DataFlowRangeAnalysisTest extends DataFlowInspectionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8_ANNOTATED;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }

  public void testLongRangeBasics() { doTest(); }

  public void testLongRangeLoop() { doTest(); }

  public void testLongRangeAnnotation() {
    myFixture.addClass("package javax.annotation;\n" +
                       "\n" +
                       "public @interface Nonnegative {}");
    doTest();
  }

  public void testLongRangeKnownMethods() {
    doTest();
  }

  public void testLongRangeMod() { doTest(); }
  public void testLongRangeDivShift() { doTest(); }

  public void testLongRangePlusMinus() { doTest(); }
  public void testLongRangeMul() { doTest(); }
  public void testFebruary31() { doTest(); }

  public void testManyAdditionsDoNotCauseExponentialBlowUp() { doTest(); }
  public void testBoxedRanges() { doTest(); }
  public void testLongRangeDiff() { doTest(); }
  public void testBackPropagation() { doTest(); }
  public void testTwoArraysDiff() { doTest(); }
  public void testModRange() { doTest(); }
  public void testBackPropagationMod() { doTest(); }
  public void testArithmeticNoOp() { doTest(); }
}
