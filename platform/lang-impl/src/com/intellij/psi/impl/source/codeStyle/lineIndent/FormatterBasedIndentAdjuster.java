/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.codeStyle.lineIndent;

import com.intellij.formatting.FormattingMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.FormattingModeAwareIndentAdjuster;
import org.jetbrains.annotations.NotNull;

public class FormatterBasedIndentAdjuster  {
  
  private final static int MAX_SYNCHRONOUS_ADJUSTMENT_DOC_SIZE = 100000;

  private FormatterBasedIndentAdjuster() {
  }

  public static void scheduleIndentAdjustment(@NotNull Project myProject,
                                              @NotNull Document myDocument,
                                              int myOffset) {
    IndentAdjusterRunnable fixer = new IndentAdjusterRunnable(myProject, myDocument, myOffset);
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    if (isSynchronousAdjustment(myDocument)) {
      documentManager.commitDocument(myDocument);
      fixer.run();
    }
    else {
      documentManager.performLaterWhenAllCommitted(fixer);
    }
  }
  
  private static boolean isSynchronousAdjustment(@NotNull Document document) {
    return ApplicationManager.getApplication().isUnitTestMode() || document.getTextLength() <= MAX_SYNCHRONOUS_ADJUSTMENT_DOC_SIZE;
  }
  
  public static class IndentAdjusterRunnable implements Runnable {
    private final Project myProject;
    private final int myLine;
    private final Document myDocument;

    public IndentAdjusterRunnable(Project project, Document document, int offset) {
      myProject = project;
      myDocument = document;
      myLine = myDocument.getLineNumber(offset);
    }

    public void run() {
      int lineStart = myDocument.getLineStartOffset(myLine);
      CommandProcessor.getInstance().runUndoTransparentAction(() ->
        ApplicationManager.getApplication().runWriteAction(() -> {
          CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
          if (codeStyleManager instanceof FormattingModeAwareIndentAdjuster) {
            ((FormattingModeAwareIndentAdjuster)codeStyleManager).adjustLineIndent(myDocument, lineStart, FormattingMode.ADJUST_INDENT_ON_ENTER);
          }
        }));
    }
  }
  
}
