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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;

public class FormatterBasedIndentAdjuster  {

  private static final String ADJUST_INDENT_COMMAND_NAME = "Adjust Indent";

  private FormatterBasedIndentAdjuster() {
  }

  public static void scheduleIndentAdjustment(@NotNull Project myProject,
                                              @NotNull Document myDocument,
                                              int myOffset) {
    IndentAdjusterRunnable fixer = new IndentAdjusterRunnable(myProject, myDocument, myOffset);
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      documentManager.commitDocument(myDocument);
      fixer.run();
    }
    else {
      documentManager.performLaterWhenAllCommitted(fixer);
    }
  }
  
  public static class IndentAdjusterRunnable implements Runnable {
    private Project myProject;
    private int myLine;
    private Document myDocument;

    public IndentAdjusterRunnable(Project project, Document document, int offset) {
      myProject = project;
      myDocument = document;
      myLine = myDocument.getLineNumber(offset);
    }

    public void run() {
      int lineStart = myDocument.getLineStartOffset(myLine);
      CommandProcessor.getInstance().executeCommand(myProject, () ->
        ApplicationManager.getApplication().runWriteAction(() -> {
          CodeStyleManager.getInstance(myProject).adjustLineIndent(myDocument, lineStart);
        }), ADJUST_INDENT_COMMAND_NAME, null);
    }
  }
  
}
