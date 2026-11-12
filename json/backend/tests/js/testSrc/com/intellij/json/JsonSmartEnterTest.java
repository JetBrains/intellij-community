package com.intellij.json;

import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessors;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.testNameFixture;

/**
 * @author Mikhail Golubev
 */
@TestApplication
@TestDataPath("$PROJECT_ROOT/community/json/backend/tests/testData")
public class JsonSmartEnterTest {
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

  private void doTest() {
    myFixture.configureByFile("smartEnter/" + myTestName + ".json");
    final List<SmartEnterProcessor> processors = SmartEnterProcessors.INSTANCE.forKey(JsonLanguage.INSTANCE);
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      final Editor editor = myFixture.getEditor();
      for (SmartEnterProcessor processor : processors) {
        processor.process(myFixture.getProject(), editor, myFixture.getFile());
      }
    });
    myFixture.checkResultByFile("smartEnter/" + myTestName + "_after.json", true);
  }

  @Test
  public void testCommaInsertedAfterArrayElement() {
    doTest();
  }

  @Test
  public void testCommaInsertedAfterProperty() {
    doTest();
  }

  @Test
  public void testCommaInsertedAfterPropertyWithMultilineValue() {
    doTest();
  }

  @Test
  public void testColonInsertedAfterPropertyKey() {
    doTest();
  }

  @Test
  public void testPropertyKeyQuotedAndCommaInserted() {
    doTest();
  }
}
