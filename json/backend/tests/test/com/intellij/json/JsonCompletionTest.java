// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.testNameFixture;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Mikhail Golubev
 */
@TestApplication
@TestDataPath("$PROJECT_ROOT/community/json/backend/tests/testData")
public class JsonCompletionTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<String> testNameFixture = testNameFixture(false);

  private CodeInsightTestFixture myFixture;
  private String myTestName;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
    myTestName = testNameFixture.get();
  }

  private static final String[] ALL_KEYWORDS = new String[]{"true", "false", "null"};
  private static final String[] NOTHING = ArrayUtilRt.EMPTY_STRING_ARRAY;

  private void doTest(String... variants) {
    myFixture.testCompletionVariants("completion/" + myTestName + ".json", variants);
  }

  private void doTestSingleVariant() {
    myFixture.configureByFile("completion/" + myTestName + ".json");
    final LookupElement[] variants = myFixture.completeBasic();
    assertNull(variants);
    myFixture.checkResultByFile("completion/" + myTestName + "_after.json" );
  }

  @Test
  public void testInsideArrayElement1() {
    doTest(ALL_KEYWORDS);
  }

  @Test
  public void testInsideArrayElement2() {
    doTest(ALL_KEYWORDS);
  }

  @Test
  public void testInsidePropertyKey1() {
    doTest(NOTHING);
  }

  @Test
  public void testInsidePropertyKey2() {
    doTest(NOTHING);
  }

  @Test
  public void testInsideStringLiteral1() {
    doTest(NOTHING);
  }

  @Test
  public void testInsideStringLiteral2() {
    doTest(NOTHING);
  }

  @Test
  public void testInsidePropertyValue() {
    doTest(ALL_KEYWORDS);
  }

  // Moved from JavaScript

  @Test
  public void testKeywords() {
    doTestSingleVariant();
  }

  @Test
  public void testKeywords_2() {
    doTestSingleVariant();
  }
}
