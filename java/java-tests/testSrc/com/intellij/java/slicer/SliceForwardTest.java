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
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.slicer.LanguageSlicing;
import com.intellij.slicer.SliceAnalysisParams;
import com.intellij.slicer.SliceHandler;
import com.intellij.slicer.SliceTestUtil;
import com.intellij.slicer.SliceUsage;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class SliceForwardTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_7_ANNOTATED);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/slice/forward/");
  }

  private void dotest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false)+".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      Map<String, RangeMarker> sliceUsageName2Offset = SliceTestUtil.extractSliceOffsetsFromDocument(myFixture.getEditor().getDocument());
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      PsiElement element = SliceHandler.create(false).getExpressionAtCaret(myFixture.getEditor(), myFixture.getFile());
      assertNotNull(element);
      SliceTestUtil.Node tree = SliceTestUtil.buildTree(element, sliceUsageName2Offset);
      Collection<HighlightInfo> errors = myFixture.doHighlighting(HighlightSeverity.ERROR);
      assertThat(errors).isNullOrEmpty();
      SliceAnalysisParams params = new SliceAnalysisParams();
      params.scope = new AnalysisScope(myFixture.getProject());
      params.dataFlowToThis = false;
      SliceUsage usage = LanguageSlicing.getProvider(element).createRootUsage(element, params);
      SliceTestUtil.checkUsages(usage, tree);
    });
  }

  @Test
  public void testSimple() { dotest();}
  @Test
  public void testInterMethod() { dotest();}
  @Test
  public void testParameters() { dotest();}
  @Test
  public void testRequireNonNull() { dotest();}
  @Test
  public void testAppend() { dotest();}
  @Test
  public void testOverloadedMember() { dotest();}
  @Test
  public void testOverloadedMember2() { dotest();}
  @Test
  public void testOverloadedMember3() { dotest();}
  @Test
  public void testOneInterfaceTwoImplementations() { dotest();}
  @Test
  public void testOneInterfaceTwoImplementations2() { dotest();}
}