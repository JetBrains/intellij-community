package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightCompletionFixture;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
@IndexingModeTemplate
public class SmartTypeCompletionDfaTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture();
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

  private void configureByTestName() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile("/" + myFixture.getTestName(false) + ".java");
    });
  }

  private void doAntiTest() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().checkResultByFile(myFixture.getTestName(false) + ".java");
      assertThat(completion.get().getItems()).isEmpty();
      assertNull(completion.get().getLookup());
    });
  }

  private void doTest() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      checkResultByTestName();
    });
  }

  private void checkResultByTestName() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkResultByFile("/" + myFixture.getTestName(false) + "-out.java");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testCastGenericQualifier() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDontAutoCastWhenAlreadyCasted() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "s", "toString");
      myFixture.type("\n");
      checkResultByTestName();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAutoCastWhenAlreadyCasted() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.type("\n");
      checkResultByTestName();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestCastedValueAfterCast() { doTest(); }

  @Test
  @NeedsIndex.Full
  public void testSuggestInstanceofedValue() { doTest(); }

  @Test
  public void testSuggestInstanceofedValueInTernary() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestInstanceofedValueInComplexIf() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestInstanceofedValueInElseNegated() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestInstanceofedValueAfterReturn() { doTest(); }

  @Test
  public void testNoInstanceofedValueWhenBasicSuits() { doTest(); }

  @Test
  public void testNoInstanceofedValueInElse() { doAntiTest(); }

  @Test
  public void testNoInstanceofedValueInThenNegated() { doAntiTest(); }

  @Test
  public void testNoInstanceofedValueInElseWithComplexIf() { doAntiTest(); }

  @Test
  public void testInstanceofedInsideAnonymous() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCastToTypeWithWildcard() { doTest(); }
}
