package com.intellij.json;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.json.formatter.JsonCodeStyleSettings;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.json.JsonTestUtil.getCustomCodeStyleSettings;
import static com.intellij.json.JsonTestUtil.getIndentOptions;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.testNameFixture;

/**
 * @author Mikhail Golubev
 */
@TestApplication
@TestDataPath("$PROJECT_ROOT/community/json/backend/tests/testData")
public class JsonEditingTest {
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

  private void doTest(@NotNull final String characters) {
    final String testName = "editing/" + myTestName;
    myFixture.configureByFile(testName + ".json");
    myFixture.type(characters);
    myFixture.checkResultByFile(testName + ".after.json");
  }

  @Test
  public void testContinuationIndentAfterPropertyKey() {
    doTest("\n");
  }

  @Test
  public void testContinuationIndentAfterColon() {
    doTest("\n");
  }

  // IDEA-130594
  @Test
  public void testNormalIndentAfterPropertyWithoutComma() {
    doTest("\n");
  }

  // WEB-13675
  @Test
  public void testIndentWithTabsWhenSmartTabEnabled() {
    final CommonCodeStyleSettings.IndentOptions indentOptions = getIndentOptions(myFixture.getProject());
    final CommonCodeStyleSettings.IndentOptions oldSettings = (CommonCodeStyleSettings.IndentOptions)indentOptions.clone();
    indentOptions.TAB_SIZE = 4;
    indentOptions.INDENT_SIZE = 4;
    indentOptions.USE_TAB_CHARACTER = true;
    indentOptions.SMART_TABS = true;
    try {
      doTest("\n\"baz\"");
    }
    finally {
      indentOptions.copyFrom(oldSettings);
    }
  }

  // Moved from JavaScript

  // WEB-11600
  @Test
  public void testEnterWhenPropertiesAlignedOnValue() {
    doEnterTestForWeb11600();
  }

  // WEB-11600
  @Test
  public void testEnterWhenPropertiesAlignedOnValue1() {
    doEnterTestForWeb11600();
  }

  private void doEnterTestForWeb11600() {
    final JsonCodeStyleSettings settings = getCustomCodeStyleSettings(myFixture.getProject());
    final CommonCodeStyleSettings.IndentOptions indentOptions = getIndentOptions(myFixture.getProject());

    final int oldPropertyAlignment = settings.PROPERTY_ALIGNMENT;
    final int oldIndentSize = indentOptions.INDENT_SIZE;
    settings.PROPERTY_ALIGNMENT = JsonCodeStyleSettings.ALIGN_PROPERTY_ON_VALUE;
    indentOptions.INDENT_SIZE = 4;
    try {
      doTest("\n");
    }
    finally {
      indentOptions.INDENT_SIZE = oldIndentSize;
      settings.PROPERTY_ALIGNMENT = oldPropertyAlignment;
    }
  }
}
