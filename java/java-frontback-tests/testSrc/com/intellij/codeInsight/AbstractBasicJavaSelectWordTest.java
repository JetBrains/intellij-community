// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.PathJavaTestUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.intellij.workspaceModel.ide.legacyBridge.impl.java.JavaModuleTypeUtils.JAVA_MODULE_ENTITY_TYPE_ID_NAME;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractBasicJavaSelectWordTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture, JAVA_MODULE_ENTITY_TYPE_ID_NAME);

  private String testMethodName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(PathJavaTestUtil.getCommunityJavaTestDataPath());
    testMethodName = testInfo.getTestMethod().orElseThrow().getName();
  }

  protected void doTest(String ext) {
    doTest(ext, true);
  }

  protected void doTest(String ext, boolean unselect) {
    doTest(ext, unselect, false);
  }

  protected void doTest(String ext, boolean unselect, boolean defaultFolding) {
    SelectWordTestUtil.doTest(codeInsightFixture.get(), testMethodName, ext, unselect, defaultFolding);
  }

  @Test
  public void testTest1() { doTest("java"); }
  @Test
  public void testTest2() { doTest("java"); }
  @Test
  public void testIfThenElse1() { doTest("java"); }
  @Test
  public void testIfThenElse2() { doTest("java"); }
  @Test
  public void testElseIf() { doTest("java"); }
  @Test
  public void testMiddleElseIf() { doTest("java"); }
  @Test
  public void testJavaDoc1() { doTest("java"); }
  @Test
  public void testParams1() { doTest("java"); }
  @Test
  public void testCall1() { doTest("java"); }
  @Test
  public void testCall2() { doTest("java"); }
  @Test
  public void testCallMultiline() { doTest("java"); }
  @Test
  public void testCallMultiline2() { doTest("java"); }
  @Test
  public void testBlock1() { doTest("java"); }
  @Test
  public void testBlock2() { doTest("java"); }
  @Test
  public void testBlock3() { doTest("java"); }
  @Test
  public void testArray1() { doTest("java"); }
  @Test
  public void testThis() { doTest("java"); }
  @Test
  public void testTryCatchFinally() { doTest("java"); }
  @Test
  public void testCast1() { doTest("java"); }

  @Test
  public void testLiteralEscape() { doTest("java", false); }

  @Test
  public void testLiteralEscape1() { doTest("java"); }
  @Test
  public void testSwitch() { doTest("java"); }
  @Test
  public void testSwitch2() { doTest("java"); }
  @Test
  public void testSwitch3() { doTest("java"); }
  @Test
  public void testSwitchStart1() { doTest("java"); }
  @Test
  public void testSwitchStart2() { doTest("java"); }
  @Test
  public void testSwitchEnd1() { doTest("java"); }
  @Test
  public void testSwitchEnd2() { doTest("java"); }
  @Test
  public void testJavaStartLine() { doTest("java"); }

  @Test
  public void testJavaEndLine() { doTest("java", false); }
  @Test
  public void testJavaTypeParameter() { doTest("java", false); }

  @Test
  public void testStringCamelHumps() {
    EdtTestUtil.runInEdtAndWait(() -> EditorSettingsExternalizable.getInstance().setCamelWords(true));
    try {
      doTest("java");
    }
    finally {
      EdtTestUtil.runInEdtAndWait(() -> EditorSettingsExternalizable.getInstance().setCamelWords(false));
    }
  }

  @Test
  public void testCommentQuotes() { doTest("java"); }
  @Test
  public void testLongCommentWithPunctuation() { doTest("java"); }
  @Test
  public void testMiddleMethod() { doTest("java"); }
  @Test
  public void testLastMethod() { doTest("java"); }
  @Test
  public void testDocumentedMethod() { doTest("java"); }
  @Test
  public void testMethodWithComment() { doTest("java"); }
  @Test
  public void testFieldWithComment() { doTest("java"); }
  @Test
  public void testSeparateFieldModifier() { doTest("java"); }
  @Test
  public void testUnderscoredWordAtComment() { doTest("java"); }
  @Test
  public void testMethodFromParameterList() { doTest("java"); }
  @Test
  public void testForHeader() { doTest("java"); }
  @Test
  public void testBlockComment() { doTest("java"); }
  @Test
  public void testAnonymousClass() { doTest("java"); }
  @Test
  public void testTwoComments() { doTest("java"); }
  @Test
  public void testWordWithApostrophe() { doTest("java"); }
  @Test
  public void testWordWithApostropheInDocComment() { doTest("java"); }
  @Test
  public void testWordWithHyphen() { doTest("java"); }
  @Test
  public void testEmptyLineInSwitchCase() { doTest("java"); }
  @Test
  public void testUnrelatedParenthesis() { doTest("java"); }
  @Test
  public void testSwitchCaseInTheMiddle() { doTest("java"); }

  @Test
  public void testTextBlockNoTrailingLine() { doTest("java"); }
  @Test
  public void testTextBlockTrailingLine() { doTest("java"); }
  @Test
  public void testTextBlockEmptyLines() { doTest("java"); }
  @Test
  public void testTextBlockEscapedLineEndings() { doTest("java"); }
  @Test
  public void testTextBlockLeadingWhitespace() { doTest("java"); }
  @Test
  public void testUnclosedTextBlock() { doTest("java"); }
  @Test
  public void testEmptyTextBlock() { doTest("java"); }
  @Test
  public void testUnclosedLiteral() { doTest("java"); }

  @Test
  public void testStringTemplate1() { doTest("java"); }
  @Test
  public void testStringTemplate2() { doTest("java"); }
  @Test
  public void testStringTemplate3() { doTest("java"); }
  @Test
  public void testStringTemplate4() { doTest("java"); }
  @Test
  public void testStringTemplate5() { doTest("java"); }
  @Test
  public void testStringTemplate6() { doTest("java"); }
  @Test
  public void testStringTemplate7() { doTest("java"); }

  @Test
  public void testLineComments() { doTest("java"); }
  @Test
  public void testLineCommentsAtStart() { doTest("java"); }
  @Test
  public void testLineCommentsAtEnd() { doTest("java"); }
  @Test
  public void testRecordParams() { doTest("java"); }

  @Test
  public void testWhitespaces() { doTest("java"); }
  @Test
  public void testWhitespacesAtFileStart() { doTest("java"); }
  @Test
  public void testWhitespacesAtFileEnd() { doTest("java"); }
  @Test
  public void testWhitespacesInTheMiddleOfTheLine() { doTest("java"); }

  @Test
  public void testEndOfFile() throws IOException {
    CodeInsightTestFixture fixture = codeInsightFixture.get();
    VirtualFile otherFile = fixture.addFileToProject("codeInsight/selectWordAction/endOfFile/zzzzzzzzzzzz.txt", StringUtil.repeat("a", 1000))
      .getVirtualFile();
    try {
      doTest("java");
      // these are preconditions actually, assuming this hasn't changed since test start
      PsiFile file = fixture.getFile();
      assertTrue(otherFile.getLength() > file.getVirtualFile().getLength());
      ReadAction.computeBlocking(() -> {
        assertSame(file.getNextSibling(), PsiManager.getInstance(fixture.getProject()).findFile(otherFile));
        return null;
      });
    }
    finally {
      WriteAction.runAndWait(() -> otherFile.delete(null));
    }
  }
}
