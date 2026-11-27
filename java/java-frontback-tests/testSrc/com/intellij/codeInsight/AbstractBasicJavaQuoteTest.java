package com.intellij.codeInsight;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.InplaceIntroduceTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

public abstract class AbstractBasicJavaQuoteTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  protected @NotNull CodeInsightTestFixture getFixture() {
    return codeInsightFixture.get();
  }

  @Test
  public void testDouble1() { doTest("<caret>", "\"<caret>\""); }

  @Test
  public void testDouble2() { doTest("<caret>\"\"", "\"<caret>\"\"\""); }

  @Test
  public void testDoubleClosing() { doTest("\"<caret>\"", "\"\"<caret>"); }

  @Test
  public void testDoubleClosingWithText() { doTest("\"text<caret>\"", "\"text\"<caret>"); }

  @Test
  public void testDoubleEscape() { doTest(" \"\\<caret>\" ", " \"\\\"<caret>\" "); }

  @Test
  public void testDoubleEscapeClosing() { doTest(" \"\\\\<caret>\" ", " \"\\\\\"<caret> "); }

  @Test
  public void testBeforeIdentifier() { doTest("foo(<caret>a);", "foo(\"<caret>a);"); }

  @Test
  public void testDoubleInString() { doTest("\"Hello\"<caret> world\";", "\"Hello\"\"<caret> world\";"); }

  @Test
  public void testBeforeStringWithEscape() { doTest("foo(P + <caret>\"\\n\" + \"xxx\" + E)", "foo(P + \"<caret>\"\"\\n\" + \"xxx\" + E)"); }

  @Test
  public void testSingleInString() {
    doTest(" \"<caret>\" ", " \"'<caret>\" ", '\'');
  }

  @Test
  public void testSingleInComment() {
    doTest("/* <caret> */", "/* '<caret> */", '\'');
  }

  @Test
  public void testSingleInStringAfterEscape() {
    doTest(" split(text, '\\<caret>); ", " split(text, '\\'<caret>); ", '\'');
  }


  @Test
  public void testDoubleQuoteInTextBlock() { doTest(" \"\"\" <caret> \"\"\" ", " \"\"\" \"<caret> \"\"\" "); }

  @Test
  public void testSingleQuoteInTextBlock() {
    doTest(" \"\"\" <caret> \"\"\" ", " \"\"\" '<caret> \"\"\" ", '\'');
  }

  @Test
  public void testTextBlockClosing() {
    doTest(" \"\"\".<caret>\"\"\" ", " \"\"\".\"<caret>\"\" ");
    doTest(" \"\"\".\"<caret>\"\" ", " \"\"\".\"\"<caret>\" ");
    doTest(" \"\"\".\"\"<caret>\" ", " \"\"\".\"\"\"<caret> ");
  }

  private void doTest(String before, String after, char c) {
    doFileTest("class C {{\n  " + before + "\n}}",
               "class C {{\n  " + after + "\n}}",
               c);
  }

  protected void doTest(String before, String after) {
    doTest(before, after, '\"');
  }

  protected void doFileTest(String completeBefore, String expectedResult, char c) {
    CodeInsightTestFixture fixture = getFixture();
    fixture.configureByText("a.java", completeBefore);
    InplaceIntroduceTestUtil.runInCommand(fixture.getProject(), () -> {
      TypedAction.getInstance().actionPerformed(fixture.getEditor(), c, ((EditorEx)fixture.getEditor()).getDataContext());
      fixture.checkResult(expectedResult);
    });
  }

  @SuppressWarnings("SameParameterValue")
  protected void doFileTest(String completeBefore, String expectedResult) {
    doFileTest(completeBefore, expectedResult, '\"');
  }
}
