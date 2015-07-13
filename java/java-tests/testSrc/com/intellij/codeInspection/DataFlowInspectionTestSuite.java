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
package com.intellij.codeInspection;

import com.intellij.codeInsight.completion.NormalCompletionDfaTest;
import com.intellij.codeInsight.completion.SmartTypeCompletionDfaTest;
import com.intellij.codeInsight.daemon.quickFix.*;
import com.intellij.slicer.SliceBackwardTest;
import com.intellij.slicer.SliceTreeTest;
import junit.framework.Test;
import junit.framework.TestSuite;

public class DataFlowInspectionTestSuite {
  public static Test suite() {
    TestSuite suite = new TestSuite();

    suite.addTestSuite(DataFlowInspectionTest.class);
    suite.addTestSuite(DataFlowInspection8Test.class);
    suite.addTestSuite(DataFlowInspectionAncientTest.class);
    suite.addTestSuite(ContractCheckTest.class);

    suite.addTestSuite(ContractInferenceFromSourceTest.class);
    suite.addTestSuite(NullityInferenceFromSourceTestCase.DfaInferenceTest.class);
    suite.addTestSuite(NullityInferenceFromSourceTestCase.LightInferenceTest.class);
    suite.addTestSuite(PurityInferenceFromSourceTest.class);

    suite.addTestSuite(SliceTreeTest.class);
    suite.addTestSuite(SliceBackwardTest.class);

    suite.addTestSuite(SmartTypeCompletionDfaTest.class);
    suite.addTestSuite(NormalCompletionDfaTest.class);

    suite.addTestSuite(NullableStuffInspectionTest.class);
    suite.addTestSuite(NullableStuffInspectionAncientTest.class);

    suite.addTestSuite(AddAssertStatementFixTest.class);
    suite.addTestSuite(SurroundWithIfFixTest.class);
    suite.addTestSuite(ReplaceWithTernaryOperatorTest.class);
    suite.addTestSuite(ReplaceWithOfNullableFixTest.class);
    suite.addTestSuite(ReplaceFromOfNullableFixTest.class);
    return suite;
  }
}
