// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions;

import com.intellij.formatting.service.CoreFormattingService;
import com.intellij.formatting.service.FormattingService;
import com.intellij.formatting.service.FormattingServiceUtil;
import com.intellij.lang.LanguageFormatting;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SequentialTask;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;

public abstract class AbstractLayoutCodeProcessor {
  private static final Logger LOG = Logger.getInstance(AbstractLayoutCodeProcessor.class);

  protected final @NotNull Project myProject;
  private final Module myModule;

  private PsiDirectory myDirectory;
  private PsiFile myFile;
  private List<PsiFile> myFiles;
  private boolean myIncludeSubdirs;

  private final @NlsContexts.ProgressText String myProgressText;
  private final @NlsContexts.Command String myCommandName;
  private Runnable myPostRunnable;
  private boolean myProcessChangedTextOnly;
  private boolean myProcessAllFilesAsSingleUndoStep = true;

  protected AbstractLayoutCodeProcessor myPreviousCodeProcessor;
  private List<VirtualFileFilter> myFilters = new ArrayList<>();

  private LayoutCodeInfoCollector myInfoCollector;

  protected AbstractLayoutCodeProcessor(@NotNull Project project, @NlsContexts.Command String commandName, @NlsContexts.ProgressText String progressText, boolean processChangedTextOnly) {
    this(project, (Module)null, commandName, progressText, processChangedTextOnly);
  }

  protected AbstractLayoutCodeProcessor(@NotNull AbstractLayoutCodeProcessor previous,
                                        @NotNull @NlsContexts.Command String commandName,
                                        @NotNull @NlsContexts.ProgressText String progressText) {
    myProject = previous.myProject;
    myModule = previous.myModule;
    myDirectory = previous.myDirectory;
    myFile = previous.myFile;
    myFiles = previous.myFiles;
    myIncludeSubdirs = previous.myIncludeSubdirs;
    myProcessChangedTextOnly = previous.myProcessChangedTextOnly;

    myPostRunnable = null;
    myProgressText = progressText;
    myCommandName = commandName;
    myPreviousCodeProcessor = previous;
    myFilters = previous.myFilters;
    myInfoCollector = previous.myInfoCollector;
  }

  protected AbstractLayoutCodeProcessor(@NotNull Project project,
                                        @Nullable Module module,
                                        @NlsContexts.Command String commandName,
                                        @NlsContexts.ProgressText String progressText,
                                        boolean processChangedTextOnly) {
    myProject = project;
    myModule = module;
    myDirectory = null;
    myIncludeSubdirs = true;
    myCommandName = commandName;
    myProgressText = progressText;
    myPostRunnable = null;
    myProcessChangedTextOnly = processChangedTextOnly;
  }

  protected AbstractLayoutCodeProcessor(@NotNull Project project,
                                        @NotNull PsiDirectory directory,
                                        boolean includeSubdirs,
                                        @NlsContexts.ProgressText String progressText,
                                        @NlsContexts.Command String commandName,
                                        boolean processChangedTextOnly) {
    myProject = project;
    myModule = null;
    myDirectory = directory;
    myIncludeSubdirs = includeSubdirs;
    myProgressText = progressText;
    myCommandName = commandName;
    myPostRunnable = null;
    myProcessChangedTextOnly = processChangedTextOnly;
  }

  protected AbstractLayoutCodeProcessor(@NotNull Project project,
                                        @NotNull PsiFile file,
                                        @NlsContexts.ProgressText String progressText,
                                        @NlsContexts.Command String commandName,
                                        boolean processChangedTextOnly) {
    myProject = project;
    myModule = null;
    myFile = file;
    myProgressText = progressText;
    myCommandName = commandName;
    myPostRunnable = null;
    myProcessChangedTextOnly = processChangedTextOnly;
  }

  protected AbstractLayoutCodeProcessor(@NotNull Project project,
                                        PsiFile @NotNull [] files,
                                        @NlsContexts.ProgressText String progressText,
                                        @NlsContexts.Command String commandName,
                                        @Nullable Runnable postRunnable,
                                        boolean processChangedTextOnly) {
    myProject = project;
    myModule = null;
    myFiles = List.of(files);
    myProgressText = progressText;
    myCommandName = commandName;
    myPostRunnable = postRunnable;
    myProcessChangedTextOnly = processChangedTextOnly;
  }

  public void setPostRunnable(Runnable postRunnable) {
    myPostRunnable = postRunnable;
  }

  public void setCollectInfo(boolean isCollectInfo) {
    myInfoCollector = isCollectInfo ? new LayoutCodeInfoCollector() : null;

    AbstractLayoutCodeProcessor current = this;
    while (current.myPreviousCodeProcessor != null) {
      current = current.myPreviousCodeProcessor;
      current.myInfoCollector = myInfoCollector;
    }
  }

  public void addFileFilter(@NotNull VirtualFileFilter filter) {
    myFilters.add(filter);
  }

  void setProcessChangedTextOnly(boolean value) {
    myProcessChangedTextOnly = value;
  }

  /**
   * @param singleUndoStep <ul>
   *                       <li>if <code>true</code> then it will be possible to Undo all files processing in one shot (at least right
   *                       after the action, until any of the files edited further). The downside is that once user edits any of the
   *                       files at all. The modal error dialog will appear: "Following files affected by this action have been already
   *                       changed".</li>
   *                       <li>if <code>false</code> then it won't be possible to Undo the action for all files in one shot, even right
   *                       after the action. The advantage is that Undo chain for each individual file won't be broken, and it will be
   *                       possible to undo this action and previous changes in each file regardless of the state of other processed files.</li>
   *                       </ul>
   */
  public void setProcessAllFilesAsSingleUndoStep(boolean singleUndoStep) {
    myProcessAllFilesAsSingleUndoStep = singleUndoStep;
  }

  /**
   * Ensures that given file is ready to reformatting and prepares it if necessary.
   *
   * @param file                    file to process
   * @param processChangedTextOnly  flag that defines is only the changed text (in terms of VCS change) should be processed
   * @return          task that triggers formatting of the given file. Returns value of that task indicates whether formatting
   *                  is finished correctly or not (exception occurred, user cancelled formatting etc)
   * @throws IncorrectOperationException    if unexpected exception occurred during formatting
   */
  protected abstract @NotNull FutureTask<Boolean> prepareTask(@NotNull PsiFile file, boolean processChangedTextOnly) throws IncorrectOperationException;

  protected static @NotNull FutureTask<Boolean> emptyTask() {
    return new FutureTask<>(EmptyRunnable.INSTANCE, true);
  }

  protected boolean needsReadActionToPrepareTask() {
    return true;
  }

  public void run() {
    if (myFile != null) {
      PsiUtilCore.ensureValid(myFile);
      VirtualFile virtualFile = PsiUtilCore.getVirtualFile(myFile);
      if (virtualFile != null) {
        runProcessFile(virtualFile);
      }
      return;
    }

    runProcessFiles();
  }

  private @NotNull FileRecursiveIterator build() {
    if (myFiles != null) {
      return new FileRecursiveIterator(myProject, ContainerUtil.filter(myFiles, AbstractLayoutCodeProcessor::canBeFormatted));
    }
    if (myProcessChangedTextOnly) {
      return buildChangedFilesIterator();
    }
    if (myDirectory != null) {
      return new FileRecursiveIterator(myDirectory);
    }
    if (myModule != null) {
      return new FileRecursiveIterator(myModule);
    }
    return new FileRecursiveIterator(myProject);
  }

  private @NotNull FileRecursiveIterator buildChangedFilesIterator() {
    List<PsiFile> files = getChangedFilesFromContext();
    return new FileRecursiveIterator(myProject, files);
  }

  private @NotNull List<PsiFile> getChangedFilesFromContext() {
    List<PsiDirectory> dirs = getAllSearchableDirsFromContext();
    return VcsFacade.getInstance().getChangedFilesFromDirs(myProject, dirs);
  }

  private List<PsiDirectory> getAllSearchableDirsFromContext() {
    List<PsiDirectory> dirs = new ArrayList<>();
    if (myDirectory != null) {
      dirs.add(myDirectory);
    }
    else if (myModule != null) {
      List<PsiDirectory> allModuleDirs = FileRecursiveIterator.collectModuleDirectories(myModule);
      dirs.addAll(allModuleDirs);
    }
    else {
      List<PsiDirectory> allProjectDirs = FileRecursiveIterator.collectProjectDirectories(myProject);
      dirs.addAll(allProjectDirs);
    }
    return dirs;
  }


  private void runProcessFile(final @NotNull VirtualFile file) {
    ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(List.of(file));
    if (status.hasReadonlyFiles()) {
      return;
    }
    Consumer<@NotNull ProgressIndicator> runnable = (indicator) -> {
      indicator.setText(myProgressText);
        try {
          new ProcessingTask(indicator).performFileProcessing(file);
        }
        catch (IndexNotReadyException e) {
          LOG.warn(e);
          return;
        }
        if (myPostRunnable != null) {
          ApplicationManager.getApplication().invokeLater(myPostRunnable);
        }
    };

    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      ProgressManager.getInstance().run(new Task.Modal(myProject, getProgressTitle(), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          runnable.accept(indicator);
        }
      });
    }
    else {
      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, getProgressTitle(), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          runnable.accept(indicator);
        }
      });
    }
  }

  private void runProcessFiles() {
    boolean isSuccess;
    try {
      isSuccess = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        return processFilesUnderProgress(indicator);
      }, getProgressTitle(), true, myProject);
    }
    catch (ProcessCanceledException e) {
      isSuccess = false;
    }

    if (isSuccess && myPostRunnable != null) {
      myPostRunnable.run();
    }
  }

  private @NotNull @NlsContexts.ProgressTitle String getProgressTitle() {
    AbstractLayoutCodeProcessor processor = getInitialProcessor();
    return processor.myCommandName;
  }

  private @NotNull AbstractLayoutCodeProcessor getInitialProcessor() {
    AbstractLayoutCodeProcessor current = this;
    while (current.myPreviousCodeProcessor != null) {
      current = current.myPreviousCodeProcessor;
    }
    return current;
  }

  private static boolean canBeFormatted(@NotNull PsiFile file) {
    if (!file.isValid()) return false;
    FormattingService formattingService = FormattingServiceUtil.findService(file, true, true);
    if (formattingService instanceof CoreFormattingService && LanguageFormatting.INSTANCE.forContext(file) == null) {
      return false;
    }
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return true;

    if (ProjectUtil.isProjectOrWorkspaceFile(virtualFile)) return false;

    return !GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(virtualFile, file.getProject());
  }

  public void runWithoutProgress() throws IncorrectOperationException {
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(myFile);
    if (virtualFile != null) {
      new ProcessingTask(new EmptyProgressIndicator()).performFileProcessing(virtualFile);
    }
  }

  public boolean processFilesUnderProgress(@NotNull ProgressIndicator indicator) {
    indicator.setIndeterminate(false);
    ProcessingTask task = new ProcessingTask(indicator);
    return task.process();
  }

  private @NotNull List<AbstractLayoutCodeProcessor> getAllProcessors() {
    AbstractLayoutCodeProcessor current = this;
    List<AbstractLayoutCodeProcessor> all = new ArrayList<>();
    while (current != null) {
      all.add(current);
      current = current.myPreviousCodeProcessor;
    }
    Collections.reverse(all);
    return all;
  }

  public static @NotNull @NlsSafe String getPresentablePath(@NotNull Project project, @NotNull PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    return vFile != null ? ProjectUtil.calcRelativeToProjectPath(vFile, project) : file.getName();
  }

  private final class ProcessingTask implements SequentialTask {
    private final List<AbstractLayoutCodeProcessor> myProcessors;

    private final FileRecursiveIterator myFileTreeIterator;
    private final FileRecursiveIterator myCountingIterator;

    private final ProgressIndicator myProgressIndicator;

    private int myTotalFiles;
    private int myFilesProcessed;
    private boolean myStopFormatting;
    private PsiFile next;

    ProcessingTask(@NotNull ProgressIndicator indicator) {
      myFileTreeIterator = ReadAction.compute(() -> build());
      myCountingIterator = ReadAction.compute(() -> build());
      myProcessors = getAllProcessors();
      myProgressIndicator = indicator;
    }

    @Override
    public boolean isDone() {
      return myStopFormatting;
    }

    @Override
    public boolean iteration() {
      if (myStopFormatting) {
        return true;
      }

      updateIndicatorFraction(myFilesProcessed);

      if (next != null) {
        PsiFile file = next;
        myFilesProcessed++;

        if (shouldProcessFile(file)) {
          updateIndicatorText(ApplicationBundle.message("bulk.reformat.process.progress.text"), ReadAction.compute(() -> getPresentablePath(myProject, file)));
          VirtualFile virtualFile = PsiUtilCore.getVirtualFile(file);
          if (virtualFile != null) {
            DumbService.getInstance(myProject).withAlternativeResolveEnabled(() -> performFileProcessing(virtualFile));
          }
        }
      }

      return true;
    }

    private Boolean shouldProcessFile(PsiFile file) {
      return ReadAction.compute(() -> file.isWritable() && canBeFormatted(file) && acceptedByFilters(file));
    }

    private void performFileProcessing(@NotNull VirtualFile file) {
      // Using the same groupId for several file-processing actions allows undoing [format + optimize imports + rearrange code + cleanup code] in one shot.
      // Using the same groupId for *all* processed files makes this a single undoable action for all processed files.
      // See docs for #setProcessAllFilesAsSingleUndoRedoCommand(boolean)
      String groupId = myProcessAllFilesAsSingleUndoStep
                       ? AbstractLayoutCodeProcessor.this.toString()
                       : AbstractLayoutCodeProcessor.this.toString() + file.hashCode();
      for (AbstractLayoutCodeProcessor processor : myProcessors) {
        FutureTask<Boolean> writeTask;
        if (processor.needsReadActionToPrepareTask()) {
          writeTask = ReadAction.nonBlocking(() -> {
              PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
              return psiFile != null ? processor.prepareTask(psiFile, myProcessChangedTextOnly) : null;
            })
            .executeSynchronously();
        }
        else {
          PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(myProject).findFile(file));
          writeTask = psiFile != null ? processor.prepareTask(psiFile, myProcessChangedTextOnly) : null;
        }
        if (writeTask == null) continue;

        ProgressIndicatorProvider.checkCanceled();

        WriteCommandAction.writeCommandAction(myProject)
          .withName(myCommandName)
          .withGroupId(groupId)
          .shouldRecordActionForActiveDocument(myProcessAllFilesAsSingleUndoStep)
          .run(() -> writeTask.run());

        checkStop(writeTask, file);
      }
    }

    private void checkStop(FutureTask<Boolean> task, @NotNull VirtualFile file) {
      try {
        if (!task.get() || task.isCancelled()) {
          myStopFormatting = true;
        }
      }
      catch (InterruptedException | ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof IndexNotReadyException) {
          LOG.warn(cause);
          return;
        }
        LOG.error("Got unexpected exception during formatting " + file, e);
      }
    }

    private void updateIndicatorText(@NotNull @NlsContexts.ProgressText String upperLabel, @NotNull @NlsContexts.ProgressDetails String downLabel) {
      myProgressIndicator.setText(upperLabel);
      myProgressIndicator.setText2(downLabel);
    }

    private void updateIndicatorFraction(int processed) {
      myProgressIndicator.setFraction((double)processed / myTotalFiles);
    }

    @Override
    public void stop() {
      myStopFormatting = true;
    }

    private boolean process() {
      myProgressIndicator.setIndeterminate(true);
      List<VirtualFile> files = new ArrayList<>();
      updateIndicatorText(ApplicationBundle.message("bulk.reformat.prepare.progress.text"), "");
      boolean success = myCountingIterator.processAll(file -> {
        files.add(file.getVirtualFile());
        return !isDone();
      });
      if (!success) return false;
      myTotalFiles = files.size();
      myProgressIndicator.setIndeterminate(false);
      Application application = ApplicationManager.getApplication();
      if (!application.isUnitTestMode()) {
        application.invokeAndWait(() -> {
          ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(files);
          if (status.hasReadonlyFiles()) {
            stop();
          }
        });
        if (isDone()) return false;
      }

      return myFileTreeIterator.processAll(file -> {
        next = file;
        iteration();
        return !isDone();
      });
    }
  }

  private boolean acceptedByFilters(@NotNull PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) {
      return false;
    }

    for (VirtualFileFilter filter : myFilters) {
      if (!filter.accept(file.getVirtualFile())) {
        return false;
      }
    }

    return true;
  }

  static List<TextRange> getSelectedRanges(@NotNull SelectionModel selectionModel) {
    final List<TextRange> ranges = new SmartList<>();
    if (selectionModel.hasSelection()) {
      TextRange range = TextRange.create(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
      ranges.add(range);
    }
    return ranges;
  }

  void handleFileTooBigException(Logger logger, FilesTooBigForDiffException e, @NotNull PsiFile file) {
    logger.info("Error while calculating changed ranges for: " + file.getVirtualFile(), e);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup("Reformat changed text");
      Notification notification = group.createNotification(
        ApplicationBundle.message("reformat.changed.text.file.too.big.notification.title"),
        ApplicationBundle.message("reformat.changed.text.file.too.big.notification.text", file.getName()),
        NotificationType.INFORMATION);
      notification.notify(file.getProject());
    }
  }

  public @Nullable LayoutCodeInfoCollector getInfoCollector() {
    return myInfoCollector;
  }
}
