/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License",
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

import com.intellij.java.codeInsight.completion.NormalCompletionDfaTest;
import com.intellij.java.codeInsight.completion.SmartTypeCompletionDfaTest;
import com.intellij.java.codeInsight.daemon.quickFix.*;
import com.intellij.java.slicer.SliceBackwardTest;
import com.intellij.java.slicer.SliceTreeTest;
import com.siyeh.ig.redundancy.RedundantOperationOnEmptyContainerInspectionTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  DataFlowInspectionTest.class,
  DataFlowInspection8Test.class,
  DataFlowInspection9Test.class,
  DataFlowInspection10Test.class,
  DataFlowInspectionHeavyTest.class,
  DataFlowInspectionAncientTest.class,
  DataFlowInspectionCancellingTest.class,
  ContractCheckTest.class,
  HardcodedContractsTest.class,
  DataFlowRangeAnalysisTest.class,
  OptionalGetWithoutIsPresentInspectionTest.class,
  RedundantOperationOnEmptyContainerInspectionTest.class,

  ContractInferenceFromSourceTest.class,
  NullityInferenceFromSourceTestCase.DfaInferenceTest.class,
  NullityInferenceFromSourceTestCase.LightInferenceTest.class,
  PurityInferenceFromSourceTest.class,
  ParameterNullityInferenceFromSourceTest.class,

  SliceTreeTest.class,
  SliceBackwardTest.class,

  SmartTypeCompletionDfaTest.class,
  NormalCompletionDfaTest.class,

  NullableStuffInspectionTest.class,
  NullableStuffInspectionAncientTest.class,

  CheckerNullityTest.class,

  AddAssertStatementFixTest.class,
  SurroundWithIfFixTest.class,
  ReplaceWithTernaryOperatorTest.class,
  ReplaceWithObjectsEqualsTest.class,
  ReplaceWithOfNullableFixTest.class,
  ReplaceWithNullCheckFixTest.class,
  ReplaceFromOfNullableFixTest.class,
  ReplaceWithTrivialLambdaFixTest.class,
  UnwrapIfStatementFixTest.class,
  StreamFilterNotNullFixTest.class,
  RedundantInstanceofFixTest.class,
  ReplaceComputeWithComputeIfPresentFixTest.class,
  DeleteSwitchLabelFixTest.class,
  })
public class DataFlowInspectionTestSuite {
}
