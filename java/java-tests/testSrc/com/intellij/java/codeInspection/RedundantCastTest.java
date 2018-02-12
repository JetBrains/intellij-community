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

import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.InspectionTestCase;

public class RedundantCastTest extends InspectionTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_3);
  }

  private void doTest() {
    doTest("redundantCast/" + getTestName(false), new RedundantCastInspection());
  }

  public void testAmbigousParm1() { doTest(); }

  public void testAmbigousParm2() { doTest(); }

  public void testAmbigousParm3() { doTest(); }

  public void testAmbigousParm4() { doTest(); }

  public void testAmbigousParm5() { doTest(); }

  public void testOneOfTwo() { doTest(); }

  public void testAnyOfTwo() { doTest(); }

  public void testNew1() { doTest(); }

  public void testAssignment1() { doTest(); }

  public void testInitializer1() { doTest(); }

  public void testShortToShort() { doTest(); }

  public void testVirtualMethod1() { doTest(); }

  public void testVirtualMethod2() { doTest(); }

  public void testVirtualMethod3() { doTest(); }

  public void testDoubleCast1() { doTest(); }

  public void testDoubleCast2() { doTest(); }

  public void testDoubleCast3() { doTest(); }

  public void testDoubleCast4() { doTest(); }

  public void testDoubleCast5() { doTest(); }

  public void testShortVsInt() { doTest(); }

  public void testTruncation() { doTest(); }

  public void testIntToDouble() { doTest(); }

  public void testSCR6907() { doTest(); }

  public void testSCR11555() { doTest(); }

  public void testSCR13397() { doTest(); }

  public void testSCR14502() { doTest(); }

  public void testSCR14559() { doTest(); }

  public void testSCR15236() { doTest(); }

  public void testComparingToNull() { doTest(); }

  public void testInaccessible() { doTest(); }

  public void testInConditional() { doTest(); }

  public void testDifferentFields() { doTest(); }

  public void testNestedThings() { doTest(); }

  public void testIDEADEV6818() { doTest(); }

  public void testIDEADEV15170() { doTest(); }

  public void testIDEADEV25675() { doTest(); }
  public void testFieldAccessOnTheLeftOfAssignment() { doTest(); }
  
  public void testNestedCast() { doTest(); }
  public void testPrimitiveInsideSynchronized() { doTest(); }

  public void testInConditionalPreserveResolve() { doTest();}
  public void testArrayAccess() { doTest();}
}
