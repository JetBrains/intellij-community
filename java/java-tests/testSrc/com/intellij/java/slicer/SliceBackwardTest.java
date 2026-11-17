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
package com.intellij.java.slicer;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.execution.filters.ExceptionAnalysisProvider;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.slicer.JavaValueFilter;
import com.intellij.slicer.LanguageSlicing;
import com.intellij.slicer.SliceAnalysisParams;
import com.intellij.slicer.SliceHandler;
import com.intellij.slicer.SliceLanguageSupportProvider;
import com.intellij.slicer.SliceTestUtil;
import com.intellij.slicer.SliceUsage;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class SliceBackwardTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_17_ANNOTATED);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/slice/backward/");
  }

  private void doTest() throws Exception {
    doTest("");
  }

  private void doTest(@NotNull String filter) throws Exception {
    doTest(filter, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  private void doTest(@NotNull String filter, @NotNull String @NotNull... stack) throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false)+".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      Map<String, RangeMarker> sliceUsageName2Offset = SliceTestUtil.extractSliceOffsetsFromDocument(myFixture.getEditor().getDocument());
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      PsiElement element = SliceHandler.create(true).getExpressionAtCaret(myFixture.getEditor(), myFixture.getFile());
      assertNotNull(element);
      SliceTestUtil.Node tree = SliceTestUtil.buildTree(element, sliceUsageName2Offset);
      Collection<HighlightInfo> errors = myFixture.doHighlighting(HighlightSeverity.ERROR);
      assertThat(errors).isNullOrEmpty();
      SliceAnalysisParams params = new SliceAnalysisParams();
      params.scope = new AnalysisScope(myFixture.getProject());
      params.dataFlowToThis = true;
      SliceLanguageSupportProvider provider = LanguageSlicing.getProvider(element);
      params.valueFilter = filter.isEmpty() ? JavaValueFilter.ALLOW_EVERYTHING : provider.parseFilter(element, filter);
      List<ExceptionAnalysisProvider.StackLine> lines = ContainerUtil.map(stack, line -> {
        String[] parts = line.split(":");
        return new ExceptionAnalysisProvider.StackLine(parts[0], parts[1], null);
      });
      assertTrue(params.valueFilter instanceof JavaValueFilter);
      params.valueFilter = ((JavaValueFilter)params.valueFilter).withStack(lines);

      SliceUsage usage = provider.createRootUsage(element, params);
      SliceTestUtil.checkUsages(usage, tree);
    });
  }

  @Test
  public void testSimple() throws Exception { doTest();}
  @Test
  public void testLocalVar() throws Exception { doTest();}
  @Test
  public void testInterMethod() throws Exception { doTest();}
  @Test
  public void testConditional() throws Exception { doTest();}
  @Test
  public void testConditional2() throws Exception { doTest();}
  @Test
  public void testMethodReturn() throws Exception { doTest();}
  @Test
  public void testVarUse() throws Exception { doTest();}
  @Test
  public void testWeirdCaretPosition() throws Exception { doTest();}
  @Test
  public void testAnonClass() throws Exception { doTest();}
  @Test
  public void testPostfix() throws Exception { doTest();}
  @Test
  public void testMethodCall() throws Exception { doTest();}
  @Test
  public void testEnumConst() throws Exception { doTest();}
  @Test
  public void testTypeAware() throws Exception { doTest();}
  @Test
  public void testTypeAware2() throws Exception { doTest();}
  @Test
  public void testViaParameterizedMethods() throws Exception { doTest();}
  @Test
  public void testTypeErased() throws Exception { doTest();}
  @Test
  public void testComplexTypeErasure() throws Exception { doTest();}
  @Test
  public void testGenericsSubst() throws Exception { doTest();}
  @Test
  public void testOverrides() throws Exception { doTest();}
  @Test
  public void testGenericBoxing() throws Exception { doTest();}
  @Test
  public void testAssignment() throws Exception { doTest();}
  @Test
  public void testGenericImplement() throws Exception { doTest();}
  @Test
  public void testGenericImplement2() throws Exception { doTest();}
  @Test
  public void testOverloadConstructor() throws Exception { doTest();}
  @Test
  public void testArrayElements() throws Exception { doTest();}
  @Test
  public void testAnonArray() throws Exception { doTest();}
  @Test
  public void testVarArgs() throws Exception { doTest();}
  @Test
  public void testVarArgsAsAWhole() throws Exception { doTest();}
  @Test
  public void testVarArgsPartial() throws Exception { doTest();}

  @Test
  public void testListTrackToArray() throws Exception { doTest();}
  @Test
  public void testTryCatchFinally() throws Exception { doTest();}
  @Test
  public void testFinalVarAssignedBeforePassingToAnonymous() throws Exception { doTest();}
  @Test
  public void testLocalVarDeclarationAndAssignment() throws Exception { doTest();}
  @Test
  public void testSearchOverriddenMethodsInThisClassHierarchy() throws Exception { doTest();}
  @Test
  public void testSearchOverriddenMethodsInThisClassHierarchyParam() throws Exception { doTest();}
  @Test
  public void testSearchOverriddenMethodsInThisClassHierarchyParamDfa() throws Exception { doTest();}
  @Test
  public void testAppend() throws Exception { doTest();}
  @Test
  public void testRequireNonNull() throws Exception { doTest();}
  @Test
  public void testBackAndForward() throws Exception { doTest();}

  @Test
  public void testFilterIntRange() throws Exception { doTest(">=0");}
  @Test
  public void testFilterIntRangeArray() throws Exception { doTest(">=0");}
  @Test
  public void testFilterNull() throws Exception { doTest("null");}
  @Test
  public void testNarrowFilter() throws Exception { doTest();}
  @Test
  public void testFilterPropagateMath() throws Exception { doTest("-8");}
  @Test
  public void testFilterPropagateBoolean() throws Exception { doTest("true");}
  @Test
  public void testFilterPropagateBoolean2() throws Exception { doTest("false");}
  @Test
  public void testFilterAssertionViolation() throws Exception { doTest("-1");}
  @Test
  public void testReturnParameter() throws Exception { doTest(); }
  @Test
  public void testFilterLongByInt() throws Exception { doTest("<=0"); }
  @Test
  public void testFilterDoubleByInt() throws Exception { doTest("0.0"); }

  @Test
  public void testStackFilterSimple() throws Exception {
    doTest("null", "MainTest:test", "MainTest:foo", "MainTest:main");
  }

  @Test
  public void testStackFilterBridgeMethod() throws Exception {
    doTest("null", "MainTest$Bar:get", "MainTest$Bar:get", "MainTest:bar", "MainTest:main");
  }

  @Test
  public void testStackFilterBridgeMethod2() throws Exception {
    doTest("null", "MainTest$Bar:get", "MainTest$Bar:get", "MainTest:bar", "MainTest:main");
  }

  @Test
  public void testRecordComponent() throws Exception { doTest();}
  @Test
  public void testRecordComponent2() throws Exception { doTest();}
  @Test
  public void testRecordComponent3() throws Exception { doTest();}
  @Test
  public void testOptionalAsContainer() throws Exception { doTest();}
  @Test
  public void testUnmodifiableList() throws Exception { doTest();}
}
