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
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.suspiciousNameCombination.SuspiciousNameCombinationInspection;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class SuspiciousNameCombinationTest extends LightJavaInspectionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/suspiciousNameCombination";
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    SuspiciousNameCombinationInspection inspection = new SuspiciousNameCombinationInspection();
    inspection.addNameGroup("someWord,otherWord");
    return inspection;
  }

  public void testAssignment() { doTest();}
  public void testInitializer() { doTest();}
  public void testParameter() { doTest();}
  public void testReturnValue() { doTest();}
  public void testExcluded() { doTest();}
  public void testTwoWords() { doTest();}
}
