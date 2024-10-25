// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.java.codeInsight.completion.NormalCompletionDfaTest;
import com.intellij.java.codeInsight.completion.SmartTypeCompletionDfaTest;
import com.intellij.java.codeInspection.dataFlow.OptionalOfNullableMisuseInspectionTest;
import com.intellij.java.slicer.SliceBackwardTest;
import com.intellij.java.slicer.SliceTreeTest;
import com.siyeh.ig.redundancy.RedundantOperationOnEmptyContainerInspectionTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
  DataFlowInspectionTest.class,
  DataFlowInspection8Test.class,
  DataFlowInspection9Test.class,
  DataFlowInspection11Test.class,
  DataFlowInspection16Test.class,
  DataFlowInspection21Test.class,
  DataFlowInspectionHeavyTest.class,
  DataFlowInspectionAncientTest.class,
  DataFlowInspectionCancellingTest.class,
  DataFlowInspectionPrimitivesInPatternsTest.class,
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
  DeleteRedundantUpdateFixTest.class,
  ReplaceTypeInCastFixTest.class,
  ReplaceMinMaxWithArgumentFixTest.class,
  OptionalOfNullableMisuseInspectionTest.class
  })
public class DataFlowInspectionTestSuite {
}
