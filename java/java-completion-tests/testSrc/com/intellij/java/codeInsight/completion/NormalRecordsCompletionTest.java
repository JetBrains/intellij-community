// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.NormalCompletionFixtureKt.normalCompletionFixture;

@TestApplication
@IndexingModeTemplate
public class NormalRecordsCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<NormalCompletionFixture> completion = normalCompletionFixture(projectFixture);

  @Test
  public void testRecordImplements() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "record X() impl<caret>");
      myFixture.completeBasic();
      myFixture.checkResult("record X() implements ");
    });
  }

  @Test
  public void testRecordNoClassAfterHeader() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "record X() cla<caret>");
      myFixture.completeBasic();
      myFixture.checkResult("record X() cla");
    });
  }

  @Test
  public void testRecordComponentName() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "record FooBar(FooBar fo<caret>)");
      myFixture.completeBasic();
      myFixture.checkResult("record FooBar(FooBar fooBar)");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInstanceOfSpecificType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        interface I<T> { }

        class J<T> implements I<T> {
          void test(I<String> i) {
            if (i instanceof <caret>)
          }
        }""");
      myFixture.completeBasic();
      assert !myFixture.getLookupElementStrings().contains("T");
      myFixture.type("\n");
      myFixture.checkResult("""
                              interface I<T> { }

                              class J<T> implements I<T> {
                                void test(I<String> i) {
                                  if (i instanceof J<String><caret>)
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for getters generation)")
  public void testRecordAccessorDeclaration() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  public void testTopLevelPublicRecord() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testTopLevelPublicRecordParenthesisExists() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testTopLevelPublicRecordBraceExists() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.SmartMode(reason = "To avoid merging")
  public void testInsertKnownConstructorParameter() {
    EdtTestUtil.runInEdtAndWait(() -> doTestWithHints());
  }

  @Test
  @NeedsIndex.SmartMode(reason = "To avoid merging")
  public void testInsertKnownConstructorParameterVoid() {
    EdtTestUtil.runInEdtAndWait(() -> doTestWithHints());
  }

  private void doTestWithHints() {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    boolean old = settings.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION;
    settings.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION = true;
    try {
      completion.get().doTest("\n");
    }
    finally {
      settings.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION = old;
    }
  }
}
