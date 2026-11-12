package com.intellij.json;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.json.formatter.JsonCodeStyleSettings.PropertyAlignment;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.json.JsonTestUtil.getCodeStyleSettings;
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
@TestDataPath("$PROJECT_ROOT/community/json/backend/tests/testData/formatting")
public class JsonFormattingTest {
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

  
  @Test
  public void testContainerElementsAlignment() {
    doTest();
  }

  @Test
  public void testBlankLinesStripping() {
    doTest();
  }

  @Test
  public void testSpacesInsertion() {
    doTest();
  }
  
  @Test
  public void testDoNotThrowFailedToAlignException() {
    getCustomCodeStyleSettings(myFixture.getProject()).PROPERTY_ALIGNMENT = PropertyAlignment.ALIGN_ON_VALUE.getId(); 
    doTest();
  }

  @Test
  public void testWrapping() {
    getCodeStyleSettings(myFixture.getProject()).setRightMargin(JsonLanguage.INSTANCE, 20);
    doTest();
  }

  @Test
  public void testWrappingWhenLineIsTooLong() {
    getCodeStyleSettings(myFixture.getProject()).setRightMargin(JsonLanguage.INSTANCE, 20);
    getCodeStyleSettings(myFixture.getProject()).getCommonSettings(JsonLanguage.INSTANCE).WRAP_LONG_LINES = true;
    doTest();
  }

  // WEB-13587
  @Test
  public void testAlignPropertiesOnColon() {
    checkPropertyAlignment(PropertyAlignment.ALIGN_ON_COLON);
  }

  // WEB-13587
  @Test
  public void testAlignPropertiesOnValue() {
    checkPropertyAlignment(PropertyAlignment.ALIGN_ON_VALUE);
  }

  private void checkPropertyAlignment(@NotNull final PropertyAlignment alignmentType) {
    getCustomCodeStyleSettings(myFixture.getProject()).PROPERTY_ALIGNMENT = alignmentType.getId();
    doTest();
  }

  @Test
  public void testChopDownArrays() {
    getCustomCodeStyleSettings(myFixture.getProject()).ARRAY_WRAPPING = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    getCodeStyleSettings(myFixture.getProject()).setRightMargin(JsonLanguage.INSTANCE, 40);
    doTest();
  }

  // IDEA-138902
  @Test
  public void testObjectsWithSingleProperty() {
    doTest();
  }

  // Moved from JavaScript

  @Test
  public void testWeb3830() {
    CommonCodeStyleSettings.IndentOptions options = getIndentOptions(myFixture.getProject());
    options.INDENT_SIZE = 8;
    options.USE_TAB_CHARACTER = true;
    options.TAB_SIZE = 8;
    doTest();
  }

  @Test
  public void testReformatJSon() {
    getIndentOptions(myFixture.getProject()).INDENT_SIZE = 4;
    doTest();
  }

  @Test
  public void testReformatJSon2() {
    getIndentOptions(myFixture.getProject()).INDENT_SIZE = 4;
    doTest();
  }

  @Test
  public void testRemoveTrailingCommas() {
    doTest();
  }

  @Test
  public void testReformatIncompleteJson1() { doTest();}

  @Test
  public void testReformatIncompleteJson2() { doTest();}

  @Test
  public void testIndentForElements() { doTest();}
  @Test
  public void testNoExtraNewLineByWrap() { doTest();}

  @PerformanceUnitTest
  @Test
  public void testHugeJsonFilePerformance() {
    // IDEA-195340 bad JSON kills IntelliJ
    myFixture.configureByFile(myTestName + ".json");
    Benchmark.newBenchmark(myTestName, this::reformatAndCheck).attempts(1).runAsStressTest().start();
  }

  private void doTest() {
    myFixture.configureByFile(myTestName + ".json");
    reformatAndCheck();
  }

  private void reformatAndCheck() {
    WriteCommandAction.runWriteCommandAction(null, () -> {
      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myFixture.getProject());
      codeStyleManager.reformat(myFixture.getFile());
    });
    myFixture.checkResultByFile(myTestName + "_after.json");
  }
}
