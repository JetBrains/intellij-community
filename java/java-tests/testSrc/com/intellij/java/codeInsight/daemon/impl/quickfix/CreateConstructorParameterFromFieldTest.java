// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.refactoring.suggested.PerformSuggestedRefactoringKt;
import com.intellij.refactoring.suggested.SuggestedRefactoringExecution;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.style.MissortedModifiersInspection;
import com.siyeh.ig.style.UnqualifiedFieldAccessInspection;
import kotlin.jvm.functions.Function1;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class CreateConstructorParameterFromFieldTest {
  private static final String TEST_DATA_PATH =
    JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/createConstructorParameterFromField";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightQuickFixFixture> quickFixFixture =
    lightQuickFixFixture(projectFixture, TEST_DATA_PATH, () -> List.of(new UnusedDeclarationInspection(), new MissortedModifiersInspection(), new UnqualifiedFieldAccessInspection()));

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(quickFixFixture.get().getFixture().getProject());
    if (fileName.contains("SameParameter")) {
      settings.PREFER_LONGER_NAMES = false;
      settings.GENERATE_FINAL_PARAMETERS = true;
    }
    Function1<Integer, SuggestedRefactoringExecution.NewParameterValue> defaultValue =
      PerformSuggestedRefactoringKt.get_suggestedChangeSignatureNewParameterValuesForTests();
    try {
      PerformSuggestedRefactoringKt.set_suggestedChangeSignatureNewParameterValuesForTests(
        idx -> new SuggestedRefactoringExecution.NewParameterValue.Expression(
          JavaPsiFacade.getElementFactory(quickFixFixture.get().getFixture().getProject()).createExpressionFromText(String.valueOf(idx), null)));
      quickFixFixture.get().doTest(fileName);
    }
    finally {
      PerformSuggestedRefactoringKt.set_suggestedChangeSignatureNewParameterValuesForTests(defaultValue);
    }
  }
}
