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
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightCompletionFixture;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionFixture;
import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
@IndexingModeTemplate
public class SmartType17CompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_7);
  private final TestFixture<LightCompletionFixture> completion =
    lightCompletionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/smartType/");

  private void complete() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().setItems(myFixture.complete(CompletionType.SMART));
    });
  }

  private void configureByFile(String path) {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(path, StringUtil.getShortName(path, '/')));
      complete();
    });
  }

  @Test
  public void testDiamondCollapsed() {
    doTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDiamondCollapsedWithMethodTypeParameter() {
    doTest();
  }

  @Test
  public void testDiamondCollapsedWithOverloadedConstructors() {
    doTest();
  }

  @Test
  public void testDiamondNotCollapsed() {
    doTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDiamondNotCollapsedNotApplicable() {
    doTest();
  }

  @Test
  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testDiamondNotCollapsedInCaseOfAnonymousClasses() {
    doTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDiamondNotCollapsedInCaseOfInapplicableInference() {
    doTest();
  }

  @Test
  public void testTryWithResourcesNoSemicolon() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testTryWithResourcesThrowsException() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile("/" + myFixture.getTestName(false) + ".java");
      myFixture.type('\n');
      completion.get().checkResultByFile("/" + myFixture.getTestName(false) + "-out.java");
    });
  }

  @Test
  public void testDiamondPresentation() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile("/" + myFixture.getTestName(false) + ".java");
      LookupElementPresentation presentation = new LookupElementPresentation();
      completion.get().getItems()[0].renderElement(presentation);
      assertEquals("MyDD<>", presentation.getItemText());
    });
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile("/" + myFixture.getTestName(false) + ".java");
      if (completion.get().getItems() != null && completion.get().getItems().length == 1) {
        final Lookup lookup = completion.get().getLookup();
        if (lookup != null) {
          completion.get().selectItem(lookup.getCurrentItem(), Lookup.NORMAL_SELECT_CHAR);
        }
      }
      completion.get().checkResultByFile("/" + myFixture.getTestName(false) + "-out.java");
    });
  }
}
