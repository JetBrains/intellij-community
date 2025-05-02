// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.BasicDefaultLightProjectDescriptor;
import com.intellij.PathJavaTestUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class AbstractBasicJavaSelectWordTest extends SelectWordTestBase {
  @Override
  protected @NotNull String getTestDataPath() {
    return PathJavaTestUtil.getCommunityJavaTestDataPath();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return new BasicDefaultLightProjectDescriptor();
  }

  public void testTest1() { doTest("java"); }
  public void testTest2() { doTest("java"); }
  public void testIfThenElse1() { doTest("java"); }
  public void testIfThenElse2() { doTest("java"); }
  public void testElseIf() { doTest("java"); }
  public void testMiddleElseIf() { doTest("java"); }
  public void testJavaDoc1() { doTest("java"); }
  public void testParams1() { doTest("java"); }
  public void testCall1() { doTest("java"); }
  public void testCall2() { doTest("java"); }
  public void testCallMultiline() { doTest("java"); }
  public void testCallMultiline2() { doTest("java"); }
  public void testBlock1() { doTest("java"); }
  public void testBlock2() { doTest("java"); }
  public void testBlock3() { doTest("java"); }
  public void testArray1() { doTest("java"); }
  public void testThis() { doTest("java"); }
  public void testTryCatchFinally() { doTest("java"); }
  public void testCast1() { doTest("java"); }

  public void testLiteralEscape() { doTest("java", false); }

  public void testLiteralEscape1() { doTest("java"); }
  public void testSwitch() { doTest("java"); }
  public void testSwitch2() { doTest("java"); }
  public void testSwitch3() { doTest("java"); }
  public void testJavaStartLine() { doTest("java"); }

  public void testJavaEndLine() { doTest("java", false); }
  public void testJavaTypeParameter() { doTest("java", false); }

  public void testStringCamelHumps() {
    EditorSettingsExternalizable.getInstance().setCamelWords(true);
    try {
      doTest("java");
    }
    finally {
      EditorSettingsExternalizable.getInstance().setCamelWords(false);
    }
  }

  public void testCommentQuotes() { doTest("java"); }
  public void testLongCommentWithPunctuation() { doTest("java"); }
  public void testMiddleMethod() { doTest("java"); }
  public void testLastMethod() { doTest("java"); }
  public void testDocumentedMethod() { doTest("java"); }
  public void testMethodWithComment() { doTest("java"); }
  public void testFieldWithComment() { doTest("java"); }
  public void testSeparateFieldModifier() { doTest("java"); }
  public void testUnderscoredWordAtComment() { doTest("java"); }
  public void testMethodFromParameterList() { doTest("java"); }
  public void testForHeader() { doTest("java"); }
  public void testBlockComment() { doTest("java"); }
  public void testAnonymousClass() { doTest("java"); }
  public void testTwoComments() { doTest("java"); }
  public void testWordWithApostrophe() { doTest("java"); }
  public void testWordWithApostropheInDocComment() { doTest("java"); }
  public void testWordWithHyphen() { doTest("java"); }
  public void testEmptyLineInSwitchCase() { doTest("java"); }
  public void testUnrelatedParenthesis() { doTest("java"); }
  public void testSwitchCaseInTheMiddle() { doTest("java"); }

  public void testTextBlockNoTrailingLine() { doTest("java"); }
  public void testTextBlockTrailingLine() { doTest("java"); }
  public void testTextBlockEmptyLines() { doTest("java"); }
  public void testTextBlockEscapedLineEndings() { doTest("java"); }
  public void testTextBlockLeadingWhitespace() { doTest("java"); }
  public void testUnclosedTextBlock() { doTest("java"); }
  public void testEmptyTextBlock() { doTest("java"); }
  public void testUnclosedLiteral() { doTest("java"); }

  public void testStringTemplate1() { doTest("java"); }
  public void testStringTemplate2() { doTest("java"); }
  public void testStringTemplate3() { doTest("java"); }
  public void testStringTemplate4() { doTest("java"); }
  public void testStringTemplate5() { doTest("java"); }
  public void testStringTemplate6() { doTest("java"); }
  public void testStringTemplate7() { doTest("java"); }

  public void testLineComments() { doTest("java"); }
  public void testLineCommentsAtStart() { doTest("java"); }
  public void testLineCommentsAtEnd() { doTest("java"); }
  public void testRecordParams() { doTest("java"); }

  public void testWhitespaces() { doTest("java"); }
  public void testWhitespacesAtFileStart() { doTest("java"); }
  public void testWhitespacesAtFileEnd() { doTest("java"); }
  public void testWhitespacesInTheMiddleOfTheLine() { doTest("java"); }

  public void testEndOfFile() throws IOException {
    VirtualFile otherFile = WriteAction.computeAndWait(() -> {
      VirtualFile res = getSourceRoot().createChildData(null, "zzzzzzzzzzzz.txt");
      VfsUtil.saveText(res, StringUtil.repeat("a", 1000));
      return res;
    });
    try {
      doTest("java");
      // these are preconditions actually, assuming this hasn't changed since test start
      assertTrue(otherFile.getLength() > getVFile().getLength());
      assertSame(getFile().getNextSibling(), PsiManager.getInstance(getProject()).findFile(otherFile));
    }
    finally {
      ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          otherFile.delete(null);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    }
  }
}
