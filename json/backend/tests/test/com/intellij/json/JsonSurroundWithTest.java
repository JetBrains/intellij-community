package com.intellij.json;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.json.surroundWith.JsonWithArrayLiteralSurrounder;
import com.intellij.json.surroundWith.JsonWithObjectLiteralSurrounder;
import com.intellij.json.surroundWith.JsonWithQuotesSurrounder;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.testNameFixture;

/**
 * @author Mikhail Golubev
 */
@TestApplication
@TestDataPath("$PROJECT_ROOT/community/json/backend/tests/testData")
public class JsonSurroundWithTest {
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

  private void doTest(Surrounder surrounder) {
    myFixture.configureByFile("/surround/" + myTestName + ".json");
    EdtTestUtil.runInEdtAndWait(() -> SurroundWithHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), surrounder));
    myFixture.checkResultByFile("/surround/" + myTestName + "_after.json", true);
  }

  @Test
  public void testSingleValue() {
    doTest(new JsonWithObjectLiteralSurrounder());
  }

  @Test
  public void testSingleProperty() {
    doTest(new JsonWithObjectLiteralSurrounder());
  }

  @Test
  public void testMultipleProperties() {
    doTest(new JsonWithObjectLiteralSurrounder());
  }

  @Test
  public void testCannotSurroundPropertyKey() {
    doTest(new JsonWithObjectLiteralSurrounder());
  }

  @Test
  public void testArrayLiteral() {
    doTest(new JsonWithArrayLiteralSurrounder());
  }

  @Test
  public void testMultipleValuesIntoArray() {
    doTest(new JsonWithArrayLiteralSurrounder());
  }

  @Test
  public void testQuotes() {
    doTest(new JsonWithQuotesSurrounder());
  }

  @Test
  public void testMultipleValuesIntoString() {
    doTest(new JsonWithQuotesSurrounder());
  }

  // Moved from JavaScript

  @Test
  public void testObjectLiteral() {
    doTest(new JsonWithObjectLiteralSurrounder());
  }
}
