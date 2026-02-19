/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ipp.asserttoif;

import com.intellij.java.codeInspection.DataFlowInspectionTest;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @see com.siyeh.ipp.asserttoif.ObjectsRequireNonNullIntention
 * @author Bas Leijdekkers
 */
public class ObjectsRequireNonNullIntentionTest extends IPPTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
  }

  public void testOne() { doTest(); }
  public void testTwo() { doTest(); }
  public void testThree() { doTest(); }
  public void testContainer() { doTest(); }

  @Override
  protected String getRelativePath() {
    return "asserttoif/objects_require_non_null";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("objects.require.non.null.intention.name");
  }
}
