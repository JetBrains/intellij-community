// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bitwise;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class NegativeIntConstantInLongContextInspectionTest {
  private static final String TEST_DATA_PATH = "/ig/com/siyeh/igtest/bitwise/negative_int";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + TEST_DATA_PATH);
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new NegativeIntConstantInLongContextInspection());
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testNegativeIntConstant() {
    doTest();
  }

  @TestApplication
  public static class NegativeIntConstantInLongContextFixTest {
    private static final String FIX_TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + TEST_DATA_PATH;
    private static final TestFixture<LightJavaProject> fixProjectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
    private final TestFixture<LightQuickFixFixture> fixQuickFixFixture =
      lightQuickFixFixture(fixProjectFixture, FIX_TEST_DATA_PATH, () -> List.of(new NegativeIntConstantInLongContextInspection()));

    static List<String> files() {
      return lightQuickFixTestFiles(FIX_TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      fixQuickFixFixture.get().doTest(fileName);
    }
  }
}
