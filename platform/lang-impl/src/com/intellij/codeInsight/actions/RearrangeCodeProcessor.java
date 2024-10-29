// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.psi.codeStyle.arrangement.engine.ArrangementEngine;
import com.intellij.util.SmartList;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.FutureTask;

public class RearrangeCodeProcessor extends AbstractLayoutCodeProcessor {

  private static final Logger LOG = Logger.getInstance(RearrangeCodeProcessor.class);
  private SelectionModel mySelectionModel;
  private final Collection<TextRange> myRanges = new ArrayList<>();

  public RearrangeCodeProcessor(@NotNull AbstractLayoutCodeProcessor previousProcessor) {
    super(previousProcessor, CodeInsightBundle.message("command.rearrange.code"), getProgressText());
  }

  public RearrangeCodeProcessor(@NotNull AbstractLayoutCodeProcessor previousProcessor, @NotNull SelectionModel selectionModel) {
    super(previousProcessor, CodeInsightBundle.message("command.rearrange.code"), getProgressText());
    mySelectionModel = selectionModel;
  }

  public RearrangeCodeProcessor(@NotNull PsiFile file, @NotNull SelectionModel selectionModel) {
    super(file.getProject(), file, getProgressText(), CodeInsightBundle.message("command.rearrange.code"), false);
    mySelectionModel = selectionModel;
  }

  public RearrangeCodeProcessor(@NotNull PsiFile file) {
    super(file.getProject(), file, getProgressText(), CodeInsightBundle.message("command.rearrange.code"), false);
  }

  @SuppressWarnings("unused") // Used in Rider
  public RearrangeCodeProcessor(@NotNull PsiFile file, TextRange[] ranges) {
    super(file.getProject(), file, getProgressText(), CodeInsightBundle.message("command.rearrange.code"), false);
    for (TextRange range : ranges) {
      if (range != null) {
        myRanges.add(range);
      }
    }
  }

  @SuppressWarnings("unused") // Required for compatibility with external plugins.
  public RearrangeCodeProcessor(@NotNull Project project,
                                PsiFile @NotNull [] files,
                                @NlsContexts.Command @NotNull String commandName,
                                @Nullable Runnable postRunnable) {
    this(project, files, commandName, postRunnable, false);
  }

  public RearrangeCodeProcessor(@NotNull Project project,
                                PsiFile @NotNull [] files,
                                @NlsContexts.Command @NotNull String commandName,
                                @Nullable Runnable postRunnable,
                                boolean processChangedTextOnly) {
    super(project, files, getProgressText(), commandName, postRunnable, processChangedTextOnly);
  }

  @Override
  protected @NotNull FutureTask<Boolean> prepareTask(final @NotNull PsiFile file, final boolean processChangedTextOnly) {
    return new FutureTask<>(() -> {
      try {
        Collection<TextRange> ranges = getRangesToFormat(file, processChangedTextOnly);
        Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);

        if (document != null && Rearranger.EXTENSION.forLanguage(file.getLanguage()) != null) {
          PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(document);
          PsiDocumentManager.getInstance(myProject).commitDocument(document);
          Runnable command = prepareRearrangeCommand(file, ranges);
          try {
            CommandProcessor.getInstance().executeCommand(myProject, command, CodeInsightBundle.message("command.rearrange.code"), null);
          }
          finally {
            PsiDocumentManager.getInstance(myProject).commitDocument(document);
          }
        }

        return true;
      }
      catch (FilesTooBigForDiffException e) {
        handleFileTooBigException(LOG, e, file);
        return false;
      }
    });
  }

  private @NotNull Runnable prepareRearrangeCommand(final @NotNull PsiFile file, final @NotNull Collection<TextRange> ranges) {
    ArrangementEngine engine = ArrangementEngine.getInstance();
    return () -> {
      engine.arrange(file, ranges);
      if (getInfoCollector() != null) {
        String info = engine.getUserNotificationInfo();
        getInfoCollector().setRearrangeCodeNotification(info);
      }
    };
  }

  public Collection<TextRange> getRangesToFormat(@NotNull PsiFile file, boolean processChangedTextOnly) throws FilesTooBigForDiffException {
    if (mySelectionModel != null) {
      return getSelectedRanges(mySelectionModel);
    }

    if (processChangedTextOnly) {
      return VcsFacade.getInstance().getChangedTextRanges(myProject, file);
    }

    return !myRanges.isEmpty() ? myRanges : new SmartList<>(file.getTextRange());
  }

  public static @NlsContexts.ProgressText String getProgressText() {
    return CodeInsightBundle.message("process.rearrange.code");
  }
}
