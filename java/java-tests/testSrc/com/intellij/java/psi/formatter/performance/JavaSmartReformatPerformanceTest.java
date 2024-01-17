// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.formatter.performance;

import com.intellij.formatting.FormatterTestUtils;
import com.intellij.java.psi.formatter.java.AbstractJavaFormatterTest;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;

import java.util.Collections;
import java.util.List;

public class JavaSmartReformatPerformanceTest extends AbstractJavaFormatterTest {
  public void testSmartReformatPerformanceInLargeFile_AsUsedInPostponedFormatting() {
    Project project = getProject();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    CommandProcessor commandProcessor = CommandProcessor.getInstance();

    PsiFile file = createFile("A.java", "");
    Document document = documentManager.getDocument(file);
    assertNotNull(document);

    String initial = loadFile("/performance/bigFile.java");
    ThrowableRunnable setup = () -> {
      Runnable command = () -> WriteAction.run(() -> document.replaceString(0, document.getTextLength(), initial));
      commandProcessor.executeCommand(project, command, null, null);
      documentManager.commitDocument(document);
    };

    ThrowableRunnable test = () -> {
      Runnable command =
        () -> WriteAction.run(() -> FormatterTestUtils.ACTIONS.get(FormatterTestUtils.Action.REFORMAT_WITH_CONTEXT).run(file, 6682, 6686));
      commandProcessor.executeCommand(project, command, null, null);
    };

    PlatformTestUtil
      .startPerformanceTest("smart reformat on big file", 500, test)
      .setup(setup)
      .warmupIterations(50)
      .attempts(100)
      .assertTiming();
    // attempt.min.ms varies below the measurement threshold
  }
}