package com.intellij.json;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.testNameFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Mikhail Golubev
 */
@TestApplication
@TestDataPath("$PROJECT_ROOT/community/json/backend/tests/testData")
public class JsonWordSelectionTest {
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

  @NotNull
  private String getDirName() {
    return "selectWord/" + myTestName;
  }

  @Test
  public void testEscapeAwareness() {
    CodeInsightTestUtil.doWordSelectionTestOnDirectory(myFixture, getDirName(), "json");
  }
  @Test
  public void testCamelHumps() {
    final String directoryName = getDirName();
    String filesExtension = "json";
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.copyDirectoryToProject(directoryName, directoryName);
      myFixture.configureByFile(directoryName + "/before." + filesExtension);
      Editor editor = myFixture.getEditor();
      boolean camelWords = editor.getSettings().isCamelWords();
      try {
        editor.getSettings().setCamelWords(true);
        int i = 1;
        while (true) {
          final String fileName = directoryName + "/after" + i + "." + filesExtension;
          if (new File(myFixture.getTestDataPath() + "/" + fileName).exists()) {
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
            myFixture.checkResultByFile(fileName);
            i++;
          }
          else {
            break;
          }
        }
        assertTrue(i > 1, "At least one 'after'-file required");
      }
      finally {
        editor.getSettings().setCamelWords(camelWords);
      }
    });
  }


}
