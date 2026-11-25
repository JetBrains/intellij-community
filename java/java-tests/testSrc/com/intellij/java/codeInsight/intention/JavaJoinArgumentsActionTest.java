// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class JavaJoinArgumentsActionTest {
  private static final String TEST_DATA_PATH =
    JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/lists/joinArguments";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightQuickFixFixture> quickFixFixture = lightQuickFixFixture(projectFixture, TEST_DATA_PATH);

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    LightQuickFixFixture quickFix = quickFixFixture.get();
    ActionHint hint = quickFix.configure(fileName);
    String contents = ReadAction.computeBlocking(() -> quickFix.getFixture().getFile().getText());
    final CommonCodeStyleSettings settings =
      CodeStyle.getSettings(quickFix.getFixture().getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    boolean myBreakAfterLparen = settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE;
    boolean myBreakBeforeRparen = settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE;
    settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = contents.contains("break after lparen");
    settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = contents.contains("break before rparen");
    try {
      quickFix.checkAction(fileName, hint);
    }
    finally {
      CommonCodeStyleSettings afterSettings =
        CodeStyle.getSettings(quickFix.getFixture().getProject()).getCommonSettings(JavaLanguage.INSTANCE);
      afterSettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = myBreakAfterLparen;
      afterSettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = myBreakBeforeRparen;
    }
  }
}
