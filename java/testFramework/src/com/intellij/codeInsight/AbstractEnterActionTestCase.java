/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight;

import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;

public abstract class AbstractEnterActionTestCase extends LightCodeInsightTestCase {
  private static final String TEST_PATH = "/codeInsight/enterAction/";

  protected void doGetIndentTest(final PsiFile file, final int lineNum, final String expected) {
    final int offset = PsiDocumentManager.getInstance(getProject()).getDocument(file).getLineEndOffset(lineNum);
    final String actial = CodeStyleManager.getInstance(getProject()).getLineIndent(file, offset);
    assertEquals(expected, actial);
  }

  protected void doTest() throws Exception {
    doTest("java");
  }

  protected void doTextTest(@NonNls String ext, @NonNls String before, @NonNls String after) throws IOException {
    configureFromFileText("a." + ext, before);
    performAction();
    checkResultByText(null, after, false);
  }

  protected void doTest(final String ext) throws Exception {
    final String name = getTestName(false);

    configureByFile(TEST_PATH + name + "." + ext);
    performAction();
    checkResultByFile(null, TEST_PATH + name + "_after." + ext, false);
  }

  protected void performAction() {
    type('\n');
  }
}
