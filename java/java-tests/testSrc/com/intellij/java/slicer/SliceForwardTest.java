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
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.slicer.*;

import java.util.Collection;
import java.util.Map;

public class SliceForwardTest extends SliceTestCase {
  @Override
  protected String getBasePath() {
    return "/java/java-tests/testData/codeInsight/slice/forward/";
  }

  private void dotest() {
    myFixture.configureByFile(getTestName(false)+".java");
    Map<String, RangeMarker> sliceUsageName2Offset = SliceTestUtil.extractSliceOffsetsFromDocument(getEditor().getDocument());
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PsiElement element = SliceHandler.create(false).getExpressionAtCaret(getEditor(), getFile());
    assertNotNull(element);
    SliceTestUtil.Node tree = SliceTestUtil.buildTree(element, sliceUsageName2Offset);
    Collection<HighlightInfo> errors = myFixture.doHighlighting(HighlightSeverity.ERROR);
    assertEmpty(errors);
    SliceAnalysisParams params = new SliceAnalysisParams();
    params.scope = new AnalysisScope(getProject());
    params.dataFlowToThis = false;
    SliceUsage usage = LanguageSlicing.getProvider(element).createRootUsage(element, params);
    SliceTestUtil.checkUsages(usage, tree);
  }

  public void testSimple() { dotest();}
  public void testInterMethod() { dotest();}
  public void testParameters() { dotest();}
  public void testRequireNonNull() { dotest();}
  public void testAppend() { dotest();}
  public void testOverloadedMember() { dotest();}
  public void testOverloadedMember2() { dotest();}
  public void testOverloadedMember3() { dotest();}
  public void testOneInterfaceTwoImplementations() { dotest();}
  public void testOneInterfaceTwoImplementations2() { dotest();}
}