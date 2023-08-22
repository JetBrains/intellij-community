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

import java.util.Collection;
import java.util.Map;

public class SliceForwardTest extends SliceTestCase {
  private void dotest() throws Exception {
    configureByFile("/codeInsight/slice/forward/"+getTestName(false)+".java");
    Map<String, RangeMarker> sliceUsageName2Offset = SliceTestUtil.extractSliceOffsetsFromDocument(getEditor().getDocument());
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PsiElement element = SliceHandler.create(false).getExpressionAtCaret(getEditor(), getFile());
    assertNotNull(element);
    SliceTestUtil.Node tree = SliceTestUtil.buildTree(element, sliceUsageName2Offset);
    Collection<HighlightInfo> errors = highlightErrors();
    assertEmpty(errors);
    SliceAnalysisParams params = new SliceAnalysisParams();
    params.scope = new AnalysisScope(getProject());
    params.dataFlowToThis = false;
    SliceUsage usage = LanguageSlicing.getProvider(element).createRootUsage(element, params);
    SliceTestUtil.checkUsages(usage, tree);
  }

  public void testSimple() throws Exception { dotest();}
  public void testInterMethod() throws Exception { dotest();}
  public void testParameters() throws Exception { dotest();}
  public void testRequireNonNull() throws Exception { dotest();}
  public void testAppend() throws Exception { dotest();}
  public void testOverloadedMember() throws Exception { dotest();}
  public void testOverloadedMember2() throws Exception { dotest();}
  public void testOverloadedMember3() throws Exception { dotest();}
  public void testOneInterfaceTwoImplementations() throws Exception { dotest();}
  public void testOneInterfaceTwoImplementations2() throws Exception { dotest();}
}