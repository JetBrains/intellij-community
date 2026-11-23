// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightCompletionFixture;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionFixture;
import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
@IndexingModeTemplate
public class NormalSwitchCompletionVariantsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightCompletionFixture> completion =
    lightCompletionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/normal/variants/");

  private static final String[] COMMON_VARIANTS = {"case", "default"};
  private static final String[] COMMON_OBJECT_VARIANTS = ArrayUtil.mergeArrays(COMMON_VARIANTS, "case null", "case null, default");

  @Test
  public void testCompletionPrimitiveTypeExpr() { doTest(COMMON_VARIANTS); }
  @Test
  public void testCompletionPrimitiveTypeStmt() { doTest(COMMON_VARIANTS); }
  @Test
  public void testCompletionVariantsInStmt() { doTest(COMMON_OBJECT_VARIANTS); }
  @Test
  public void testCompletionVariantsInExpr() { doTest(COMMON_OBJECT_VARIANTS); }
  @Test
  public void testCompletionWhenAfterTypeTest() {
    EdtTestUtil.runInEdtAndWait(() -> {
      List<String> lookup = doTestAndGetLookup();
      assertThat(lookup).contains("when");
    });
  }
  @Test
  public void testCompletionWhenAfterDeconstruction() {
    EdtTestUtil.runInEdtAndWait(() -> {
      List<String> lookup = doTestAndGetLookup();
      assertThat(lookup).contains("when");
    });
  }
  @Test
  public void testCompletionWhenAfterPartDeconstruction() {
    EdtTestUtil.runInEdtAndWait(() -> {
      List<String> lookup = doTestAndGetLookup();
      if (lookup != null) {
        assertThat(lookup).doesNotContain("when");
      }
      //if null - it is ok
    });
  }

  @Test
  public void testCompletionWhenAfterPartTypeTest() {
    EdtTestUtil.runInEdtAndWait(() -> {
      List<String> lookup = doTestAndGetLookup();
      if (lookup != null) {
        assertThat(lookup).doesNotContain("when");
      }
      //if null - it is ok
    });
  }

  @Test
  public void testCompletionDefaultNotShow() {
    EdtTestUtil.runInEdtAndWait(() -> {
      List<String> lookup = doTestAndGetLookup();
      if (lookup != null) {
        assertThat(lookup).doesNotContain("default");
        assertThat(lookup).doesNotContain("case null, default");
      }
      //if null - it is ok
    });
  }

  @Test
  public void testCompletionNullDefault() {
    EdtTestUtil.runInEdtAndWait(() -> {
      List<String> lookup = doTestAndGetLookup();
      assertNotNull(lookup);
      assertThat(lookup).contains("case null", "case null, default", "default");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testCompletionUsedSealedNotShow() {
    EdtTestUtil.runInEdtAndWait(() -> {
      List<String> lookup = doTestAndGetLookup();
      assertNotNull(lookup);
      assertThat(lookup).doesNotContain("case A");
      assertThat(lookup).contains("case B");
    });
  }
  @Test
  @NeedsIndex.Full
  public void testCompletionAnonymousAndLocalNotShow() {
    EdtTestUtil.runInEdtAndWait(() -> {
      List<String> lookup = doTestAndGetLookup();
      assertNotNull(lookup);
      assertThat(lookup).contains("case C1");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testCompletionSmartCase() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21, () -> {
        myFixture.configureByFile(myFixture.getTestName(false) + ".java");
        myFixture.complete(CompletionType.SMART);
        List<String> lookup = myFixture.getLookupElementStrings();
        assertThat(lookup).contains("case A", "case B", "default", "case null", "case null, default");
      });
    });
  }

  @Test
  @NeedsIndex.Full
  public void testCompletionSealedHierarchyStmt() {
    doTest(ArrayUtil.mergeArrays(COMMON_OBJECT_VARIANTS, "case Variant1", "case Variant2"));
  }

  @Test
  @NeedsIndex.Full
  public void testCompletionSealedHierarchyExpr() {
    doTest(ArrayUtil.mergeArrays(COMMON_OBJECT_VARIANTS, "case Variant1", "case Variant2"));
  }
  @Test
  @NeedsIndex.Full
  public void testCompletionSealedHierarchyExprBeforeCase() {
    doTest(new String[]{"case null", "case", "case R", "case R2"});
  }

  @Test
  public void testCompletionObjectBeforeDefault() {
    doTest(new String[]{"case", "case null"});
  }

  @Test
  public void testCompletionObjectAfterDefault() {
    doTest(ArrayUtil.EMPTY_STRING_ARRAY);
  }

  private void doTest(String[] variants) {
    EdtTestUtil.runInEdtAndWait(() -> {
      final List<String> lookupElementStrings = doTestAndGetLookup();
      assertThat(lookupElementStrings).containsOnly(variants);
    });
  }
  private List<String> doTestAndGetLookup() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    return EdtTestUtil.runInEdtAndGet(() -> {
      myFixture.configureByFile(myFixture.getTestName(false) + ".java");
      myFixture.complete(CompletionType.BASIC);

      return myFixture.getLookupElementStrings();
    });
  }
}
