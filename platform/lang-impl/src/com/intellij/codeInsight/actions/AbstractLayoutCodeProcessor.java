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

package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lang.LanguageFormatting;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ExceptionUtil;
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
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public abstract class AbstractLayoutCodeProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor");

  protected final Project myProject;
  private final Module myModule;

  private PsiDirectory myDirectory;
  private PsiFile myFile;
  private List<PsiFile> myFiles;
  private boolean myIncludeSubdirs;

  private final String myProgressText;
  private final String myCommandName;
  private Runnable myPostRunnable;
  private boolean myProcessChangedTextOnly;

  protected AbstractLayoutCodeProcessor myPreviousCodeProcessor;
  private List<VirtualFileFilter> myFilters = ContainerUtil.newArrayList();

  private LayoutCodeInfoCollector myInfoCollector;

  protected AbstractLayoutCodeProcessor(Project project, String commandName, String progressText, boolean processChangedTextOnly) {
    this(project, (Module)null, commandName, progressText, processChangedTextOnly);
  }

  protected AbstractLayoutCodeProcessor(@NotNull AbstractLayoutCodeProcessor previous,
                                        @NotNull String commandName,
                                        @NotNull String progressText)
  {
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

  protected AbstractLayoutCodeProcessor(Project project,
                                        @Nullable Module module,
                                        String commandName,
                                        String progressText,
                                        boolean processChangedTextOnly)
  {
    myProject = project;
    myModule = module;
    myDirectory = null;
    myIncludeSubdirs = true;
    myCommandName = commandName;
    myProgressText = progressText;
    myPostRunnable = null;
    myProcessChangedTextOnly = processChangedTextOnly;
  }

  protected AbstractLayoutCodeProcessor(Project project,
                                        PsiDirectory directory,
                                        boolean includeSubdirs,
                                        String progressText,
                                        String commandName,
                                        boolean processChangedTextOnly)
  {
    myProject = project;
    myModule = null;
    myDirectory = directory;
    myIncludeSubdirs = includeSubdirs;
    myProgressText = progressText;
    myCommandName = commandName;
    myPostRunnable = null;
    myProcessChangedTextOnly = processChangedTextOnly;
  }

  protected AbstractLayoutCodeProcessor(Project project,
                                        PsiFile file,
                                        String progressText,
                                        String commandName,
                                        boolean processChangedTextOnly)
  {
    myProject = project;
    myModule = null;
    myFile = file;
    myProgressText = progressText;
    myCommandName = commandName;
    myPostRunnable = null;
    myProcessChangedTextOnly = processChangedTextOnly;
  }

  protected AbstractLayoutCodeProcessor(Project project,
                                        PsiFile[] files,
                                        String progressText,
                                        String commandName,
                                        @Nullable Runnable postRunnable,
                                        boolean processChangedTextOnly)
  {
    myProject = project;
    myModule = null;
    myFiles = filterFilesTo(files, new ArrayList<>());
    myProgressText = progressText;
    myCommandName = commandName;
    myPostRunnable = postRunnable;
    myProcessChangedTextOnly = processChangedTextOnly;
  }

  private static List<PsiFile> filterFilesTo(PsiFile[] files, List<PsiFile> list) {
    for (PsiFile file : files) {
      if (canBeFormatted(file)) {
        list.add(file);
      }
    }
    return list;
  }

  public void setPostRunnable(Runnable postRunnable) {
    myPostRunnable = postRunnable;
  }

  @Nullable
  private FutureTask<Boolean> getPreviousProcessorTask(@NotNull PsiFile file, boolean processChangedTextOnly) {
    return myPreviousCodeProcessor != null ? myPreviousCodeProcessor.preprocessFile(file, processChangedTextOnly)
                                           : null;
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

  protected void setProcessChangedTextOnly(boolean value) {
    myProcessChangedTextOnly = value;
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
  @NotNull
  protected abstract FutureTask<Boolean> prepareTask(@NotNull PsiFile file, boolean processChangedTextOnly) throws IncorrectOperationException;

  public FutureTask<Boolean> preprocessFile(@NotNull PsiFile file, boolean processChangedTextOnly) throws IncorrectOperationException {
    final FutureTask<Boolean> previousTask = getPreviousProcessorTask(file, processChangedTextOnly);
    final FutureTask<Boolean> currentTask = prepareTask(file, processChangedTextOnly);

    return new FutureTask<>(() -> {
      try {
        if (previousTask != null) {
          previousTask.run();
          if (!previousTask.get() || previousTask.isCancelled()) return false;
        }

        ApplicationManager.getApplication().runWriteAction(currentTask);

        return currentTask.get() && !currentTask.isCancelled();
      }
      catch (ExecutionException e) {
        ExceptionUtil.rethrowUnchecked(e.getCause());
        throw e;
      }
    });
  }

  public void run() {
    if (myFile != null) {
      runProcessFile(myFile);
      return;
    }

    FileTreeIterator iterator;
    if (myFiles != null) {
      iterator = new FileTreeIterator(myFiles);
    }
    else {
      iterator = myProcessChangedTextOnly ? buildChangedFilesIterator()
                                          : buildFileTreeIterator();
    }
    runProcessFiles(iterator);
  }

  private FileTreeIterator buildFileTreeIterator() {
    if (myDirectory != null) {
      return new FileTreeIterator(myDirectory);
    }
    else if (myFiles != null) {
      return new FileTreeIterator(myFiles);
    }
    else if (myModule != null) {
      return new FileTreeIterator(myModule);
    }
    else if (myProject != null) {
      return new FileTreeIterator(myProject);
    }

    return new FileTreeIterator(Collections.emptyList());
  }

  @NotNull
  private FileTreeIterator buildChangedFilesIterator() {
    List<PsiFile> files = getChangedFilesFromContext();
    return new FileTreeIterator(files);
  }

  @NotNull
  private List<PsiFile> getChangedFilesFromContext() {
    List<PsiDirectory> dirs = getAllSearchableDirsFromContext();
    return FormatChangedTextUtil.getChangedFilesFromDirs(myProject, dirs);
  }

  private List<PsiDirectory> getAllSearchableDirsFromContext() {
    List<PsiDirectory> dirs = ContainerUtil.newArrayList();
    if (myDirectory != null) {
      dirs.add(myDirectory);
    }
    else if (myModule != null) {
      List<PsiDirectory> allModuleDirs = FileTreeIterator.collectModuleDirectories(myModule);
      dirs.addAll(allModuleDirs);
    }
    else if (myProject != null) {
      List<PsiDirectory> allProjectDirs = FileTreeIterator.collectProjectDirectories(myProject);
      dirs.addAll(allProjectDirs);
    }
    return dirs;
  }


  private void runProcessFile(@NotNull final PsiFile file) {
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);

    if (document == null) {
      return;
    }

    if (!FileDocumentManager.getInstance().requestWriting(document, myProject)) {
      Messages.showMessageDialog(myProject, PsiBundle.message("cannot.modify.a.read.only.file", file.getName()),
                                 CodeInsightBundle.message("error.dialog.readonly.file.title"),
                                 Messages.getErrorIcon()
      );
      return;
    }

    final Ref<FutureTask<Boolean>> writeActionRunnable = new Ref<>();
    Runnable readAction = () -> {
      if (!checkFileWritable(file)) return;
      try{
        FutureTask<Boolean> writeTask = preprocessFile(file, myProcessChangedTextOnly);
        writeActionRunnable.set(writeTask);
      }
      catch(IncorrectOperationException e){
        LOG.error(e);
      }
    };
    Runnable writeAction = () -> {
      if (writeActionRunnable.isNull()) return;
      FutureTask<Boolean> task = writeActionRunnable.get();
      task.run();
      try {
        task.get();
      }
      catch (CancellationException ignored) {
      }
      catch (ExecutionException e) {
        if (e.getCause() instanceof IndexNotReadyException) {
          throw (IndexNotReadyException)e.getCause();
        }
        LOG.error(e);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    };
    runLayoutCodeProcess(readAction, writeAction);
  }

  private boolean checkFileWritable(final PsiFile file){
    if (!file.isWritable()){
      MessagesEx.fileIsReadOnly(myProject, file.getVirtualFile())
          .setTitle(CodeInsightBundle.message("error.dialog.readonly.file.title"))
          .showLater();
      return false;
    }
    else{
      return true;
    }
  }

  private void runProcessFiles(@NotNull final FileTreeIterator fileIterator) {
    boolean isSuccess = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      ReformatFilesTask task = new ReformatFilesTask(fileIterator, indicator);
      while (!task.isDone()) {
        task.iteration();
      }
    }, myCommandName, true, myProject);

    if (isSuccess && myPostRunnable != null) {
      myPostRunnable.run();
    }
  }

  private static boolean canBeFormatted(PsiFile file) {
    if (LanguageFormatting.INSTANCE.forContext(file) == null) {
      return false;
    }
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return true;

    if (ProjectUtil.isProjectOrWorkspaceFile(virtualFile)) return false;

    return !GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(virtualFile, file.getProject());
  }

  private void runLayoutCodeProcess(final Runnable readAction, final Runnable writeAction) {
    final ProgressWindow progressWindow = new ProgressWindow(true, myProject);
    progressWindow.setTitle(myCommandName);
    progressWindow.setText(myProgressText);

    final ModalityState modalityState = ModalityState.current();

    final Runnable process = () -> ApplicationManager.getApplication().runReadAction(readAction);

    Runnable runnable = () -> {
      try {
        ProgressManager.getInstance().runProcess(process, progressWindow);
      }
      catch(ProcessCanceledException e) {
        return;
      }
      catch(IndexNotReadyException e) {
        LOG.warn(e);
        return;
      }

      final Runnable writeRunnable = () -> CommandProcessor.getInstance().executeCommand(myProject, () -> {
        try {
          writeAction.run();

          if (myPostRunnable != null) {
            ApplicationManager.getApplication().invokeLater(myPostRunnable);
          }
        }
        catch (IndexNotReadyException e) {
          LOG.warn(e);
        }
      }, myCommandName, null);

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        writeRunnable.run();
      }
      else {
        ApplicationManager.getApplication().invokeLater(writeRunnable, modalityState, myProject.getDisposed());
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().executeOnPooledThread(runnable);
    }
  }

  public void runWithoutProgress() throws IncorrectOperationException {
    final Runnable runnable = preprocessFile(myFile, myProcessChangedTextOnly);
    runnable.run();
  }

  private List<AbstractLayoutCodeProcessor> getAllProcessors() {
    AbstractLayoutCodeProcessor current = this;
    List<AbstractLayoutCodeProcessor> all = ContainerUtil.newArrayList();
    while (current != null) {
      all.add(current);
      current = current.myPreviousCodeProcessor;
    }
    Collections.reverse(all);
    return all;
  }

  private class ReformatFilesTask implements SequentialTask {
    private final List<AbstractLayoutCodeProcessor> myProcessors;
    
    private final FileTreeIterator myFileTreeIterator;
    private final FileTreeIterator myCountingIterator;
    
    private final ProgressIndicator myProgressIndicator;

    private int myTotalFiles = 0;
    private int myFilesProcessed = 0;
    private boolean myStopFormatting;
    private boolean myFilesCountingFinished;

    ReformatFilesTask(@NotNull FileTreeIterator fileIterator, @NotNull ProgressIndicator indicator) {
      myFileTreeIterator = fileIterator;
      myCountingIterator = new FileTreeIterator(fileIterator);
      myProcessors = getAllProcessors();
      myProgressIndicator = indicator;
    }

    @Override
    public void prepare() {
    }

    @Override
    public boolean isDone() {
      return myStopFormatting || !hasFilesToProcess(myFileTreeIterator);
    }

    private void countingIteration() {
      if (hasFilesToProcess(myCountingIterator)) {
        nextFile(myCountingIterator);
        myTotalFiles++;
      }
      else {
        myFilesCountingFinished = true;
      }
    }

    @Override
    public boolean iteration() {
      if (myStopFormatting) {
        return true;
      }

      if (!myFilesCountingFinished) {
        updateIndicatorText(ApplicationBundle.message("bulk.reformat.prepare.progress.text"), "");
        countingIteration();
        return true;
      }

      updateIndicatorFraction(myFilesProcessed);

      if (hasFilesToProcess(myFileTreeIterator)) {
        PsiFile file = nextFile(myFileTreeIterator);
        myFilesProcessed++;

        if (shouldProcessFile(file)) {
          updateIndicatorText(ApplicationBundle.message("bulk.reformat.process.progress.text"), getPresentablePath(file));
          DumbService.getInstance(myProject).withAlternativeResolveEnabled(() -> performFileProcessing(file));
        }
      }

      return true;
    }

    @NotNull
    private PsiFile nextFile(FileTreeIterator it) {
      return ReadAction.compute(it::next);
    }

    private boolean hasFilesToProcess(FileTreeIterator it) {
      return it.hasNext();
    }

    private Boolean shouldProcessFile(PsiFile file) {
      Computable<Boolean> computable = () -> file.isWritable() && canBeFormatted(file) && acceptedByFilters(file);
      return ApplicationManager.getApplication().runReadAction(computable);
    }

    private void performFileProcessing(@NotNull PsiFile file) {
      for (AbstractLayoutCodeProcessor processor : myProcessors) {
        FutureTask<Boolean> writeTask = ReadAction.compute(() -> processor.prepareTask(file, myProcessChangedTextOnly));

        ProgressIndicatorProvider.checkCanceled();

        ApplicationManager.getApplication().invokeAndWait(() -> WriteCommandAction.runWriteCommandAction(myProject, myCommandName, null, writeTask));

        checkStop(writeTask, file);
      }
    }

    private void checkStop(FutureTask<Boolean> task, PsiFile file) {
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

    private void updateIndicatorText(@NotNull String upperLabel, @NotNull String downLabel) {
      myProgressIndicator.setText(upperLabel);
      myProgressIndicator.setText2(downLabel);
    }

    private String getPresentablePath(@NotNull PsiFile file) {
      VirtualFile vFile = file.getVirtualFile();
      return vFile != null ? ProjectUtil.calcRelativeToProjectPath(vFile, myProject) : file.getName();
    }

    private void updateIndicatorFraction(int processed) {
      myProgressIndicator.setFraction((double)processed / myTotalFiles);
    }

    @Override
    public void stop() {
      myStopFormatting = true;
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

  protected static List<TextRange> getSelectedRanges(@NotNull SelectionModel selectionModel) {
    final List<TextRange> ranges = new SmartList<>();
    if (selectionModel.hasSelection()) {
      TextRange range = TextRange.create(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
      ranges.add(range);
    }
    return ranges;
  }

  protected void handleFileTooBigException(Logger logger, FilesTooBigForDiffException e, @NotNull PsiFile file) {
    logger.info("Error while calculating changed ranges for: " + file.getVirtualFile(), e);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      Notification notification = new Notification(ApplicationBundle.message("reformat.changed.text.file.too.big.notification.groupId"),
                                                   ApplicationBundle.message("reformat.changed.text.file.too.big.notification.title"),
                                                   ApplicationBundle.message("reformat.changed.text.file.too.big.notification.text", file.getName()),
                                                   NotificationType.INFORMATION);
      notification.notify(file.getProject());
    }
  }

  @Nullable
  public LayoutCodeInfoCollector getInfoCollector() {
    return myInfoCollector;
  }
}
