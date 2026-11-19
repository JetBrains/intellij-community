// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.LightCompletionFixture;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionFixture;
import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;

@TestApplication
@IndexingModeTemplate
public class TabCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture();
  private final TestFixture<LightCompletionFixture> completion =
    lightCompletionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/normal");

  @Test
  public void testMethodCallCompletionWithTab() {
    configureByFile("MethodLookup3.java");
    checkResultByFile("MethodLookup3_After.java");
  }

  @Test
  public void testReplaceThisWithSuper() {
    configureByTestName();
    checkResultJava();
  }

  @Test
  public void testReplaceThisWithSuper2() {
    configureByTestName();
    checkResultJava();
  }

  @Test
  @NeedsIndex.SmartMode(reason = "Smart completion in dumb mode is not supported for txt, properties and xml")
  public void testTabInXml() {
    configureByFile("TabInXml.xml");
    checkResultByFile("TabInXml_After.xml");
  }

  @Test
  @NeedsIndex.SmartMode(reason = "Smart completion in dumb mode is not supported for txt, properties and xml")
  public void testTabInXml2() {
    configureByFile("TabInXml2.xml");
    checkResultByFile("TabInXml2_After.xml");
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testMethodCallBeforeAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByFile("MethodCallBeforeAnnotation.java");
      myFixture.completeBasic();
      myFixture.type("tos\t");
    });
    checkResultJava();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testMethodCallBeforeAnnotation2() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByFile("MethodCallBeforeAnnotation2.java");
      myFixture.completeBasic();
      myFixture.type("tos\t");
    });
    checkResultJava();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testReplaceStringLiteral() {
    configureByTestName();
    checkResultJava();
  }

  private void checkResultJava() {
    checkResultByFile(completion.get().getTestName(false) + "_After.java");
  }

  private void checkResultByFile(String path) {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkResultByFile(path));
  }

  private void configureByTestName() {
    configureByFile("/" + completion.get().getTestName(false) + ".java");
  }

  /**
   * Opens {@code path}, completes, and selects the first item with a tab, as the {@code complete} override of the JUnit 3 class did.
   */
  private void configureByFile(String path) {
    LightCompletionFixture fixture = completion.get();
    LightJavaCodeInsightTestFixture myFixture = fixture.getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(path, StringUtil.getShortName(path, '/')));
      LookupElement[] items = myFixture.completeBasic();
      fixture.selectItem(items[0], '\t');
    });
  }
}
