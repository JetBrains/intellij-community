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
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.psi.codeStyle.arrangement.engine.ArrangementEngine;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class RearrangeCodeProcessor extends AbstractLayoutCodeProcessor {

  public static final String COMMAND_NAME = "Rearrange code";
  public static final String PROGRESS_TEXT = "Rearranging code...";

  @Nullable private SelectionModel mySelectionModel;

  public RearrangeCodeProcessor(@NotNull AbstractLayoutCodeProcessor previousProcessor) {
    super(previousProcessor, COMMAND_NAME, PROGRESS_TEXT);
  }

  public RearrangeCodeProcessor(@NotNull AbstractLayoutCodeProcessor previousProcessor, @NotNull SelectionModel selectionModel) {
    super(previousProcessor, COMMAND_NAME, PROGRESS_TEXT);
    mySelectionModel = selectionModel;
  }
  
  public RearrangeCodeProcessor(@NotNull Project project,
                                @NotNull PsiFile file,
                                @Nullable SelectionModel selectionModel) {
    super(project, file, PROGRESS_TEXT, COMMAND_NAME, false);
    mySelectionModel = selectionModel;
  }

  public RearrangeCodeProcessor(@NotNull Project project,
                                @NotNull PsiFile[] files,
                                @NotNull String commandName,
                                @Nullable Runnable postRunnable) {
    super(project, files, PROGRESS_TEXT, commandName, postRunnable, false);
  }

  @NotNull
  @Override
  protected FutureTask<Boolean> prepareTask(@NotNull final PsiFile file, final boolean processChangedTextOnly) {
    return new FutureTask<Boolean>(new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        Collection<TextRange> ranges = processChangedTextOnly ? FormatChangedTextUtil.getChangedTextRanges(myProject, file)
                                                              : getRangesToFormat(file);

        RearrangeCommand rearranger = new RearrangeCommand(myProject, file, COMMAND_NAME, ranges);
        if (rearranger.couldRearrange()) {
          rearranger.run();
        }
        return true;
      }
    });
  }

  public Collection<TextRange> getRangesToFormat(@NotNull PsiFile file) {
    final List<TextRange> ranges = new SmartList<TextRange>();
    if (mySelectionModel != null && mySelectionModel.hasSelection()) {
      ranges.add(TextRange.create(mySelectionModel.getSelectionStart(), mySelectionModel.getSelectionEnd()));
    }
    else {
      ranges.add(TextRange.create(0, file.getTextLength()));
    }
    return ranges;
  }
}


class RearrangeCommand {
  @NotNull private PsiFile myFile;
  @NotNull private String myCommandName;
  @NotNull private Project myProject;
  private Document myDocument;
  private Runnable myCommand;
  private final Collection<TextRange> myRanges;

  RearrangeCommand(@NotNull Project project, @NotNull PsiFile file, @NotNull String commandName, @NotNull Collection<TextRange> ranges) {
    myProject = project;
    myFile = file;
    myRanges = ranges;
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
        engine.arrange(myFile, myRanges);
      }
    };
    PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(myDocument);
  }
}
