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

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.slicer.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/**
 * @author cdr
 */
public class SliceBackwardTest extends SliceTestCase {
  private void doTest() throws Exception {
    doTest("");
  }

  private void doTest(@NotNull String filter) throws Exception {
    configureByFile("/codeInsight/slice/backward/"+getTestName(false)+".java");
    Map<String, RangeMarker> sliceUsageName2Offset = SliceTestUtil.extractSliceOffsetsFromDocument(getEditor().getDocument());
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PsiElement element = SliceHandler.create(true).getExpressionAtCaret(getEditor(), getFile());
    assertNotNull(element);
    SliceTestUtil.Node tree = SliceTestUtil.buildTree(element, sliceUsageName2Offset);
    Collection<HighlightInfo> errors = highlightErrors();
    assertEmpty(errors);
    SliceAnalysisParams params = new SliceAnalysisParams();
    params.scope = new AnalysisScope(getProject());
    params.dataFlowToThis = true;
    SliceLanguageSupportProvider provider = LanguageSlicing.getProvider(element);
    if (!filter.isEmpty()) {
      params.valueFilter = provider.parseFilter(element, filter);
    }

    SliceUsage usage = provider.createRootUsage(element, params);
    SliceTestUtil.checkUsages(usage, tree);
  }

  public void testSimple() throws Exception { doTest();}
  public void testLocalVar() throws Exception { doTest();}
  public void testInterMethod() throws Exception { doTest();}
  public void testConditional() throws Exception { doTest();}
  public void testConditional2() throws Exception { doTest();}
  public void testMethodReturn() throws Exception { doTest();}
  public void testVarUse() throws Exception { doTest();}
  public void testWeirdCaretPosition() throws Exception { doTest();}
  public void testAnonClass() throws Exception { doTest();}
  public void testPostfix() throws Exception { doTest();}
  public void testMethodCall() throws Exception { doTest();}
  public void testEnumConst() throws Exception { doTest();}
  public void testTypeAware() throws Exception { doTest();}
  public void testTypeAware2() throws Exception { doTest();}
  public void testViaParameterizedMethods() throws Exception { doTest();}
  public void testTypeErased() throws Exception { doTest();}
  public void testComplexTypeErasure() throws Exception { doTest();}
  public void testGenericsSubst() throws Exception { doTest();}
  public void testOverrides() throws Exception { doTest();}
  public void testGenericBoxing() throws Exception { doTest();}
  public void testAssignment() throws Exception { doTest();}
  public void testGenericImplement() throws Exception { doTest();}
  public void testGenericImplement2() throws Exception { doTest();}
  public void testOverloadConstructor() throws Exception { doTest();}
  public void testArrayElements() throws Exception { doTest();}
  public void testAnonArray() throws Exception { doTest();}
  public void testVarArgs() throws Exception { doTest();}
  public void testVarArgsAsAWhole() throws Exception { doTest();}
  public void testVarArgsPartial() throws Exception { doTest();}

  public void testListTrackToArray() throws Exception { doTest();}
  public void testTryCatchFinally() throws Exception { doTest();}
  public void testFinalVarAssignedBeforePassingToAnonymous() throws Exception { doTest();}
  public void testLocalVarDeclarationAndAssignment() throws Exception { doTest();}
  public void testSearchOverriddenMethodsInThisClassHierarchy() throws Exception { doTest();}
  public void testAppend() throws Exception { doTest();}
  public void testRequireNonNull() throws Exception { doTest();}
  public void testFilterIntRange() throws Exception { doTest(">=0");}
  public void testFilterNull() throws Exception { doTest("null");}
}
