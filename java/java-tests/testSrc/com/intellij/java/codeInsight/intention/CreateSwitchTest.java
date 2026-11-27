// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.java.JavaBundle;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dmitry Batkovich
 */
@TestApplication
public class CreateSwitchTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    javaModuleCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/createSwitch/");

  private String testName;

  @BeforeEach
  void setUpTestName(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), true);
  }

  @Test
  public void testEnum() { doTest(); }
  @Test
  public void testEnum2() { doTest(); }
  @Test
  public void testString() { IdeaTestUtil.withLevel(codeInsightFixture.get().getModule(), LanguageLevel.JDK_1_7, this::doTest); }
  @Test
  public void testPrimitive() { doTest(); }
  @Test
  public void testBoxedType() { doTest(); }
  @Test
  public void testNotAvailable() { doTestNotAvailable(); }
  @Test
  public void testNotAvailable2() { doTestNotAvailable(); }
  @Test
  public void testNotAvailableInForUpdate() { doTestNotAvailable(); }
  @Test
  public void testNotAvailableInAssignment() { doTestNotAvailable(); }
  @Test
  public void testNotAvailableOnRedCode() { IdeaTestUtil.withLevel(codeInsightFixture.get().getModule(), LanguageLevel.JDK_1_7, this::doTestNotAvailable); }
  @Test
  public void testNotFailingOnBadEscapes() { IdeaTestUtil.withLevel(codeInsightFixture.get().getModule(), LanguageLevel.JDK_1_7, this::doTestNotAvailable); }
  @Test
  public void testNotAvailableOnLiteral() { doTestNotAvailable(); }
  @Test
  public void testPatternMatching() { IdeaTestUtil.withLevel(codeInsightFixture.get().getModule(), LanguageLevel.JDK_21, this::doTest); }
  @Test
  public void testNoPatternMatching() { doTestNotAvailable(); }

  private void doTest() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String name = testName;
    EdtTestUtil.runInEdtAndWait(() -> CodeInsightTestUtil.doIntentionTest(myFixture, JavaBundle.message("intention.create.switch.statement"), name + ".java", name + "_after.java"));
  }

  private void doTestNotAvailable() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testName + ".java");
    assertThat(myFixture.filterAvailableIntentions(JavaBundle.message("intention.create.switch.statement"))).isNullOrEmpty();
  }
}
