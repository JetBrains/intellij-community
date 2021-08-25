// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.actions;

import com.intellij.CodeStyleBundle;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.formatting.FormattingProgressTask;
import com.intellij.formatting.KeptLineFeedsCollector;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.ChangedRangesInfo;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.FutureTask;

public class ReformatCodeProcessor extends AbstractLayoutCodeProcessor {
  private static final Logger LOG = Logger.getInstance(ReformatCodeProcessor.class);
  private static final Key<Long> SECOND_FORMAT_KEY = Key.create("second.format");

  private final Collection<TextRange> myRanges = new ArrayList<>();
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
    file.putUserData(SECOND_FORMAT_KEY, file.getModificationStamp());
  }

  @Override
  protected boolean needsReadActionToPrepareTask() {
    return false;
  }

  @Override
  @NotNull
  protected FutureTask<Boolean> prepareTask(@NotNull final PsiFile file, final boolean processChangedTextOnly)
    throws IncorrectOperationException
  {
    PsiFile fileToProcess = ReadAction.compute(() -> ensureValid(file));
    if (fileToProcess == null) return new FutureTask<>(() -> false);
    boolean doNotKeepLineBreaks = confirmSecondReformat(file);
    return new FutureTask<>(() -> {
      Ref<Boolean> result = new Ref<>();
      CodeStyle.doWithTemporarySettings(myProject, CodeStyle.getSettings(fileToProcess), (settings) -> {
        if (doNotKeepLineBreaks) {
          settings.getCommonSettings(fileToProcess.getLanguage()).KEEP_LINE_BREAKS = false;
        }
        result.set(doReformat(file, processChangedTextOnly));
      });
      return result.get() ;
    });
  }

  private boolean confirmSecondReformat(@NotNull PsiFile file) {
    CodeStyleSettings defaultSettings = CodeStyle.getSettings(myProject);
    Ref<Boolean> doNotKeepLineBreaks = Ref.create(isDoNotKeepLineBreaks(file));
    String key = "second.reformat.confirmed";
    if (doNotKeepLineBreaks.get() && !defaultSettings.ENABLE_SECOND_REFORMAT && !PropertiesComponent.getInstance().isValueSet(key)) {
      ApplicationManager.getApplication().invokeAndWait(() -> {
        doNotKeepLineBreaks.set(
          MessageDialogBuilder.yesNo(CodeInsightBundle.message("second.reformat"),
                                     CodeInsightBundle.message("do.you.want.to.remove.custom.line.breaks")).doNotAsk(new DoNotAskOption.Adapter() {
            @Override
            public void rememberChoice(boolean isSelected, int exitCode) {
              if (isSelected) {
                PropertiesComponent.getInstance().setValue(key,
                                                           defaultSettings.ENABLE_SECOND_REFORMAT = exitCode == DialogWrapper.OK_EXIT_CODE);
              }
            }
          }).ask(myProject));
      });
    }
    return doNotKeepLineBreaks.get();
  }

  private boolean doReformat(@NotNull PsiFile fileToProcess, boolean processChangedTextOnly) {
    FormattingProgressTask.FORMATTING_CANCELLED_FLAG.set(false);
    try {
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(fileToProcess);
      final LayoutCodeInfoCollector infoCollector = getInfoCollector();
      LOG.assertTrue(infoCollector == null || document != null);

      CharSequence before = document == null
       ? null
       : document.getImmutableCharSequence();
      KeptLineFeedsCollector.setup(fileToProcess);
      try {
        EditorScrollingPositionKeeper.perform(document, true, () -> SlowOperations.allowSlowOperations(() -> {
          if (processChangedTextOnly) {
            ChangedRangesInfo info = VcsFacade.getInstance().getChangedRangesInfo(fileToProcess);
            if (info != null) {
              assertFileIsValid(fileToProcess);
              CodeStyleManager.getInstance(myProject).reformatTextWithContext(fileToProcess, info);
            }
          }
          else {
            Collection<TextRange> ranges = getRangesToFormat(fileToProcess);
            CodeStyleManager.getInstance(myProject).reformatText(fileToProcess, ranges);
          }
        }));
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

      return !FormattingProgressTask.FORMATTING_CANCELLED_FLAG.get();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return false;
    }
    finally {
      myRanges.clear();
    }
  }

  private static boolean isDoNotKeepLineBreaks(PsiFile file) {
    Long cachedValue = SECOND_FORMAT_KEY.get(file);
    return cachedValue != null && cachedValue == file.getModificationStamp();
  }

  @Nullable
  private static PsiFile ensureValid(@NotNull PsiFile file) {
    if (file.isValid()) return file;

    VirtualFile virtualFile = file.getVirtualFile();
    if (!virtualFile.isValid()) return null;

    FileViewProvider provider = file.getManager().findViewProvider(virtualFile);
    if (provider == null) return null;

    Language language = file.getLanguage();
    return provider.hasLanguage(language) ? provider.getPsi(language) : provider.getPsi(provider.getBaseLanguage());
  }

  private static void assertFileIsValid(@NotNull PsiFile file) {
    if (!file.isValid()) {
      LOG.error(
        "Invalid Psi file, name: " + file.getName() +
        " , class: " + file.getClass().getSimpleName() +
        " , " + PsiInvalidElementAccessException.findOutInvalidationReason(file));
    }
  }

  private void prepareUserNotificationMessage(@NotNull Document document, @NotNull CharSequence before) {
    LOG.assertTrue(getInfoCollector() != null);
    int number = VcsFacade.getInstance().calculateChangedLinesNumber(document, before);
    if (number > 0) {
      String message = CodeInsightBundle.message("hint.text.formatted.line", number);
      getInfoCollector().setReformatCodeNotification(message);
    }
  }

  @NotNull
  private Collection<TextRange> getRangesToFormat(PsiFile file) {
    if (mySelectionModel != null) {
      return getSelectedRanges(mySelectionModel);
    }
    
    return !myRanges.isEmpty() ? myRanges : ContainerUtil.newArrayList(file.getTextRange());
  }

  private static @NlsContexts.ProgressText String getProgressText() {
    return CodeStyleBundle.message("reformat.progress.common.text");
  }

  public static @NlsContexts.Command String getCommandName() {
    return CodeStyleBundle.message("process.reformat.code");
  }
}