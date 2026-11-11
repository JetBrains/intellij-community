// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.platform.bazel.runfiles.BazelLabel;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.common.BazelTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.internationalization.ConvertToBasicLatinInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class ConvertToBasicLatinTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private static String getTestDataPath() {
    if (BazelTestUtil.isUnderBazelTest()) {
      return BazelTestUtil.getFileFromBazelRuntime(BazelLabel.Companion.fromString("@community//plugins/java-i18n:testData"))
        .resolve("quickFix/convertToBasicLatin").toString();
    }
    return PathManager.getCommunityHomePath() + "/plugins/java-i18n/testData/quickFix/convertToBasicLatin";
  }

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(getTestDataPath());
    myFixture.enableInspections(new ConvertToBasicLatinInspection());
  }

  @Test
  public void testCharLiteral() { doTest(); }
  @Test
  public void testStringLiteral() { doTest(); }
  @Test
  public void testTextBlock() { doTest(); }
  @Test
  public void testStringTemplate1() { doTest(); }
  @Test
  public void testStringTemplate2() { doTest(); }
  @Test
  public void testStringTemplate3() { doTest(); }
  @Test
  public void testStringTemplate4() { doTest(); }
  @Test
  public void testStringTemplate5() { doTest(); }
  @Test
  public void testStringTemplate6() { doTest(); }
  @Test
  public void testPlainComment() { doTest(); }
  @Test
  public void testDocComment() { doTest(); }
  @Test
  public void testDocCommentEntities() { doTest(); }
  @Test
  public void testDocTag() { doTest(); }
  @Test
  public void testUnclosedStringLiteral() { doTest(); }
  @Test
  public void testUnclosedPlainComment() { doTest(); }
  @Test
  public void testUnclosedDocComment() { doTest(); }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles(myFixture.getTestName(false) + ".java");
    final IntentionAction singleIntention = myFixture.findSingleIntention(
      InspectionGadgetsBundle.message("inspection.non.basic.latin.character.quickfix"));
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(singleIntention);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile(myFixture.getTestName(false) + ".java", myFixture.getTestName(false) + "_after.java", true);
    });
  }
}