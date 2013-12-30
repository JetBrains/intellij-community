/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.actions;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.psi.codeStyle.arrangement.engine.ArrangementEngine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class RearrangeCodeProcessor extends AbstractLayoutCodeProcessor {

  public static final String COMMAND_NAME = "Rearrange code";
  public static final String PROGRESS_TEXT = "Rearranging code...";

  @Nullable private Condition<PsiFile> myAcceptCondition;

  public RearrangeCodeProcessor(@NotNull AbstractLayoutCodeProcessor previousProcessor,
                                @Nullable Condition<PsiFile> acceptCondition) {
    super(previousProcessor, COMMAND_NAME, PROGRESS_TEXT);
    myAcceptCondition = acceptCondition;
  }

  public boolean shouldRearrangeFile(@NotNull PsiFile file) {
    return myAcceptCondition == null || myAcceptCondition.value(file);
  }

  @NotNull
  @Override
  protected FutureTask<Boolean> prepareTask(@NotNull final PsiFile file, boolean processChangedTextOnly) {
    return new FutureTask<Boolean>(new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        if (!shouldRearrangeFile(file)) return true;

        RearrangeCommand rearranger = new RearrangeCommand(myProject, file, COMMAND_NAME);
        if (rearranger.couldRearrange()) {
          rearranger.run();
          return true;
        }
        return false;
      }
    });
  }

}


class RearrangeCommand {
  @NotNull private PsiFile myFile;
  @NotNull private String myCommandName;
  @NotNull private Project myProject;
  private Document myDocument;
  private Runnable myCommand;

  RearrangeCommand(@NotNull Project project, @NotNull PsiFile file, @NotNull String commandName) {
    myProject = project;
    myFile = file;
    myCommandName = commandName;
    myDocument = PsiDocumentManager.getInstance(project).getDocument(file);
  }

  boolean couldRearrange() {
    return myDocument != null && Rearranger.EXTENSION.forLanguage(myFile.getLanguage()) != null;
  }

  void run() {
    assert myDocument != null;
    prepare();
    try {
      CommandProcessor.getInstance().executeCommand(myProject, myCommand, myCommandName, null);
    }
    finally {
      PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
    }
  }

  private void prepare() {
    final ArrangementEngine engine = ServiceManager.getService(myProject, ArrangementEngine.class);
    myCommand = new Runnable() {
      @Override
      public void run() {
        engine.arrange(myFile, Collections.singleton(myFile.getTextRange()));
      }
    };
    PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(myDocument);
  }
}
