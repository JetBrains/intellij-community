/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.slicer;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.IntArrayList;
import gnu.trove.TIntObjectHashMap;

import java.util.Collection;
import java.util.Map;

/**
 * @author cdr
 */
public class SliceBackwardTest extends SliceTestCase {
  private final TIntObjectHashMap<IntArrayList> myFlownOffsets = new TIntObjectHashMap<IntArrayList>();

  private void doTest() throws Exception {
    configureByFile("/codeInsight/slice/backward/"+getTestName(false)+".java");
    Map<String, RangeMarker> sliceUsageName2Offset = SliceTestUtil.extractSliceOffsetsFromDocument(getEditor().getDocument());
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PsiElement element = new SliceHandler(true).getExpressionAtCaret(getEditor(), getFile());
    assertNotNull(element);
    SliceTestUtil.calcRealOffsets(element, sliceUsageName2Offset, myFlownOffsets);
    Collection<HighlightInfo> errors = highlightErrors();
    assertEmpty(errors);
    SliceAnalysisParams params = new SliceAnalysisParams();
    params.scope = new AnalysisScope(getProject());
    params.dataFlowToThis = true;

    SliceUsage usage = LanguageSlicing.getProvider(element).createRootUsage(element, params);
    SliceTestUtil.checkUsages(usage, myFlownOffsets);
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
}
