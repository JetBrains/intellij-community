// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.actions;

import com.intellij.CodeStyleBundle;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.formatting.KeptLineFeedsCollector;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.ChangedRangesInfo;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import com.intellij.psi.impl.source.codeStyle.CodeFormattingData;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.FutureTask;

public class ReformatCodeProcessor extends AbstractLayoutCodeProcessor {
  private static final Logger LOG = CodeStyle.LOG;
  private static final Key<Trinity<Long, Date, List<TextRange>>> SECOND_FORMAT_KEY = Key.create("second.format");
  private static final String SECOND_REFORMAT_CONFIRMED = "second.reformat.confirmed.2";

  private final List<TextRange> myRanges = new ArrayList<>();
  private SelectionModel mySelectionModel;

  public ReformatCodeProcessor(Project project, boolean processChangedTextOnly) {
    super(project, getCommandName(), getProgressText(), processChangedTextOnly);
  }

  public ReformatCodeProcessor(@NotNull PsiFile file, @NotNull SelectionModel selectionModel) {
    super(file.getProject(), file, getProgressText(), getCommandName(), false);
    mySelectionModel = selectionModel;
  }

  public ReformatCodeProcessor(AbstractLayoutCodeProcessor processor, @NotNull SelectionModel selectionModel) {
    super(processor, getCommandName(), getProgressText());
    mySelectionModel = selectionModel;
  }

  public ReformatCodeProcessor(AbstractLayoutCodeProcessor processor, boolean processChangedTextOnly) {
    super(processor, getCommandName(), getProgressText());
    setProcessChangedTextOnly(processChangedTextOnly);
  }

  public ReformatCodeProcessor(Project project, Module module, boolean processChangedTextOnly) {
    super(project, module, getCommandName(), getProgressText(), processChangedTextOnly);
  }

  public ReformatCodeProcessor(Project project, PsiDirectory directory, boolean includeSubdirs, boolean processChangedTextOnly) {
    super(project, directory, includeSubdirs, getProgressText(), getCommandName(), processChangedTextOnly);
  }

  public ReformatCodeProcessor(Project project, PsiFile file, @Nullable TextRange range, boolean processChangedTextOnly) {
    super(project, file, getProgressText(), getCommandName(), processChangedTextOnly);
    if (range != null) {
      myRanges.add(range);
    }
  }

  @SuppressWarnings("unused") // Used in Rider
  public ReformatCodeProcessor(@NotNull PsiFile file, TextRange[] ranges) {
    super(file.getProject(), file, getProgressText(), getCommandName(), false);
    for (TextRange range : ranges) {
      if (range != null) {
        myRanges.add(range);
      }
    }
  }

  public ReformatCodeProcessor(@NotNull PsiFile file, boolean processChangedTextOnly) {
    super(file.getProject(), file, getProgressText(), getCommandName(), processChangedTextOnly);
  }

  public ReformatCodeProcessor(Project project, PsiFile[] files, @Nullable Runnable postRunnable, boolean processChangedTextOnly) {
    this(project, files, getCommandName(), postRunnable, processChangedTextOnly);
  }

  public ReformatCodeProcessor(Project project,
                               PsiFile[] files,
                               @NlsContexts.Command String commandName,
                               @Nullable Runnable postRunnable,
                               boolean processChangedTextOnly)
  {
    super(project, files, getProgressText(), commandName, postRunnable, processChangedTextOnly);
  }

  public void setDoNotKeepLineBreaks(PsiFile file) {
    file.putUserData(SECOND_FORMAT_KEY, Trinity.create(file.getModificationStamp(), new Date(), myRanges));
  }

  @Override
  protected boolean needsReadActionToPrepareTask() {
    return false;
  }

  @Override
  protected @NotNull FutureTask<Boolean> prepareTask(final @NotNull PsiFile file, final boolean processChangedTextOnly)
    throws IncorrectOperationException
  {
    Pair<PsiFile, Runnable> fileToFormatAndCommitActionIfNeed = ReadAction.compute(() -> {
      PsiFile psiFile = ensureValid(file);
      if (psiFile != null) {
        PsiDocumentManager instance = PsiDocumentManager.getInstance(myProject);
        Document document = instance.getDocument(psiFile);
        if (document != null) {
          return Pair.create(psiFile, () -> instance.commitDocument(document));
        }
      }
      return Pair.create(psiFile, null);
    });

    PsiFile fileToProcess = fileToFormatAndCommitActionIfNeed.first;
    if (fileToProcess == null) {
      return new FutureTask<>(() -> false);
    }

    Computable<List<TextRange>> prepareRangesForFormat = () -> {
      List<TextRange> formattingRanges = getRangesToFormat(file, processChangedTextOnly);
      CodeFormattingData.prepare(fileToProcess, formattingRanges);
      return formattingRanges;
    };

    Ref<List<TextRange>> rangesForFormat = Ref.create();
    final Runnable commitAction = fileToFormatAndCommitActionIfNeed.second;
    if (commitAction == null) {
      rangesForFormat.set(ReadAction.compute(() -> prepareRangesForFormat.compute()));
    }

    boolean doNotKeepLineBreaks = confirmSecondReformat(file);
    return new FutureTask<>(() -> {
      Ref<Boolean> result = new Ref<>();
      CodeStyle.runWithLocalSettings(myProject, CodeStyle.getSettings(fileToProcess), (settings) -> {
        if (doNotKeepLineBreaks) {
          settings.getCommonSettings(fileToProcess.getLanguage()).KEEP_LINE_BREAKS = false;
        }
        if (commitAction != null) {
          commitAction.run();
          rangesForFormat.set(prepareRangesForFormat.compute());
        }
        result.set(doReformat(file, rangesForFormat.get(), processChangedTextOnly));
      });
      return result.get();
    });
  }

  private static boolean isSecondReformatDisabled() {
    return !CodeInsightSettings.getInstance().ENABLE_SECOND_REFORMAT && PropertiesComponent.getInstance().isValueSet(SECOND_REFORMAT_CONFIRMED);
  }

  private boolean confirmSecondReformat(@NotNull PsiFile file) {
    boolean doNotKeepLineBreaks = ReadAction.compute(() -> isDoNotKeepLineBreaks(file));
    if (!doNotKeepLineBreaks || isSecondReformatDisabled()) return false;
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (!settings.ENABLE_SECOND_REFORMAT) {
      Ref<Boolean> ref = Ref.create(true);
      ApplicationManager.getApplication().invokeAndWait(() -> {
        ref.set(
          MessageDialogBuilder.yesNo(CodeInsightBundle.message("second.reformat"),
                                     CodeInsightBundle.message("do.you.want.to.remove.custom.line.breaks"))
            .doNotAsk(new DoNotAskOption.Adapter() {
              @Override
              public void rememberChoice(boolean isSelected, int exitCode) {
                if (isSelected) {
                  settings.ENABLE_SECOND_REFORMAT = exitCode == DialogWrapper.OK_EXIT_CODE;
                  PropertiesComponent.getInstance().setValue(SECOND_REFORMAT_CONFIRMED, true);
                }
              }
            }).ask(myProject));
      });
      return ref.get();
    }
    return true;
  }

  private boolean doReformat(@NotNull PsiFile file, List<TextRange> ranges, boolean processChangedTextOnly) {
    PsiFile fileToProcess = ensureValid(file);
    if (fileToProcess == null) {
      LOG.warn("Invalid file " + file.getName() + ", skipping reformat");
      return false;
    }
    CodeFormatterFacade.FORMATTING_CANCELLED_FLAG.set(false);
    try {
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(fileToProcess);
      final LayoutCodeInfoCollector infoCollector = getInfoCollector();
      LOG.assertTrue(infoCollector == null || document != null);

      CharSequence before = document == null ? null : document.getImmutableCharSequence();
      if (!isSecondReformatDisabled()) {
        KeptLineFeedsCollector.setup(fileToProcess);
      }
      try {
        if (LOG.isDebugEnabled()) {
          LOG.debug("explicit reformat for " + file.getName());
        }
        CodeStyleManager.getInstance(myProject).reformatText(fileToProcess, ranges, processChangedTextOnly);
      }
      catch (ProcessCanceledException pce) {
        if (before != null) {
          document.setText(before);
        }
        if (infoCollector != null) {
          infoCollector.setReformatCodeNotification(CodeInsightBundle.message("hint.text.formatting.canceled"));
        }
         return false;
      }
      finally {
        List<Segment> segments = KeptLineFeedsCollector.getLineFeedsAndCleanup();
        if (!segments.isEmpty() && infoCollector != null) {
          infoCollector.setSecondFormatNotification(CodeInsightBundle.message("hint.text.custom.line.breaks.are.preserved"));
          setDoNotKeepLineBreaks(fileToProcess);
        }
        else {
          fileToProcess.putUserData(SECOND_FORMAT_KEY, null);
        }
      }

      if (infoCollector != null) {
        prepareUserNotificationMessage(document, before);
      }

      return !CodeFormatterFacade.FORMATTING_CANCELLED_FLAG.get();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return false;
    }
    finally {
      myRanges.clear();
    }
  }

  private boolean isDoNotKeepLineBreaks(PsiFile file) {
    Trinity<Long, Date, List<TextRange>> previous = SECOND_FORMAT_KEY.get(file);
    return previous != null && previous.first == file.getModificationStamp() &&
           (new Date().getTime() - previous.second.getTime() < 5000) &&
           myRanges.equals(previous.third);
  }

  private static @Nullable PsiFile ensureValid(@NotNull PsiFile file) {
    if (file.isValid()) return file;

    VirtualFile virtualFile = file.getVirtualFile();
    if (!virtualFile.isValid()) return null;

    FileViewProvider provider = file.getManager().findViewProvider(virtualFile);
    if (provider == null) return null;

    Language language = file.getLanguage();
    return provider.hasLanguage(language) ? provider.getPsi(language) : provider.getPsi(provider.getBaseLanguage());
  }

  private void prepareUserNotificationMessage(@NotNull Document document, @NotNull CharSequence before) {
    LOG.assertTrue(getInfoCollector() != null);
    int number = VcsFacade.getInstance().calculateChangedLinesNumber(document, before);
    if (number > 0) {
      String message = CodeInsightBundle.message("hint.text.formatted.line", number);
      getInfoCollector().setReformatCodeNotification(message);
    }
  }

  private @NotNull List<TextRange> getRangesToFormat(@NotNull PsiFile file, boolean processChangedTextOnly) {
    if (processChangedTextOnly) {
      ChangedRangesInfo info = VcsFacade.getInstance().getChangedRangesInfo(file);
      return info != null ? info.allChangedRanges : Collections.emptyList();
    }
    if (mySelectionModel != null) {
      return getSelectedRanges(mySelectionModel);
    }

    return !myRanges.isEmpty() ? myRanges : List.of(file.getTextRange());
  }

  private static @NlsContexts.ProgressText String getProgressText() {
    return CodeStyleBundle.message("reformat.progress.common.text");
  }

  public static @NlsContexts.Command String getCommandName() {
    return CodeStyleBundle.message("process.reformat.code");
  }
}