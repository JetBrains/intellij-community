/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.psi.formatter.performance;

import com.intellij.java.psi.formatter.java.AbstractJavaFormatterTest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaSmartReformatPerformanceTest extends AbstractJavaFormatterTest {


  public void testSmartReformatPerformanceInLargeFile_AsUsedInPostponedFormatting() {
    String initial = loadFile("/performance/bigFile.java");

    final PsiFile file = createFile("A.java", "");
    PsiDocumentManager manager = PsiDocumentManager.getInstance(getProject());
    final Document document = manager.getDocument(file);
    if (document == null) {
      fail("Document is null");
      return;
    }

    List<TextRange> ranges = ContainerUtil.newArrayList(new TextRange(6682, 6686));

    PlatformTestUtil
      .startPerformanceTest("smart reformat on big file", 110, getReformatRunnable(file, ranges))
      .setup(getSetupRunnable(initial, document))
      .useLegacyScaling().assertTiming();

  }

  private ThrowableRunnable getSetupRunnable(@NotNull String initial, @NotNull Document document) {
    return () -> {
      CommandProcessor.getInstance().executeCommand(
        getProject(),
        () -> ApplicationManager.getApplication().runWriteAction(
          () -> document.replaceString(0, document.getTextLength(), initial)
        ),
        null,
        null);
      PsiDocumentManager.getInstance(getProject()).commitDocument(document);
    };
  }

  private ThrowableRunnable getReformatRunnable(@NotNull PsiFile file, @NotNull List<TextRange> ranges) {
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(getProject());
    return () -> CommandProcessor.getInstance().executeCommand(
      getProject(),
      () -> ApplicationManager.getApplication().runWriteAction(
        () -> codeStyleManager.reformatTextWithContext(file, ranges)
      ),
      null,
      null);
  }
}
