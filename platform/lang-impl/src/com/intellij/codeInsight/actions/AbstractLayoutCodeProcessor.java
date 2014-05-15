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

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
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
  private final Runnable myPostRunnable;
  private final boolean myProcessChangedTextOnly;

  protected AbstractLayoutCodeProcessor myPreviousCodeProcessor;

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
    myFiles = filterFilesTo(files, new ArrayList<PsiFile>());
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

  @Nullable
  private FutureTask<Boolean> getPreviousProcessorTask(@NotNull PsiFile file, boolean processChangedTextOnly) {
    return myPreviousCodeProcessor != null ? myPreviousCodeProcessor.preprocessFile(file, processChangedTextOnly)
                                           : null;
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

    return new FutureTask<Boolean>(new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        if (previousTask != null) {
          previousTask.run();
          if (!previousTask.get() || previousTask.isCancelled()) return false;
        }

        currentTask.run();
        return currentTask.get() && !currentTask.isCancelled();
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

    return new FileTreeIterator(Collections.<PsiFile>emptyList());
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

    final Runnable[] resultRunnable = new Runnable[1];
    Runnable readAction = new Runnable() {
      @Override
      public void run() {
        if (!checkFileWritable(file)) return;
        try{
          resultRunnable[0] = preprocessFile(file, myProcessChangedTextOnly);
        }
        catch(IncorrectOperationException e){
          LOG.error(e);
        }
      }
    };
    Runnable writeAction = new Runnable() {
      @Override
      public void run() {
        if (resultRunnable[0] != null) {
          resultRunnable[0].run();
        }
      }
    };
    runLayoutCodeProcess(readAction, writeAction, false );
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

  @Nullable
  private Runnable createIterativeFileProcessor(@NotNull final FileTreeIterator fileIterator) {
    return new Runnable() {
      @Override
      public void run() {
        SequentialModalProgressTask progressTask = new SequentialModalProgressTask(myProject, myCommandName);
        progressTask.setMinIterationTime(100);
        ReformatFilesTask reformatFilesTask = new ReformatFilesTask(fileIterator);
        reformatFilesTask.setCompositeTask(progressTask);
        progressTask.setTask(reformatFilesTask);
        ProgressManager.getInstance().run(progressTask);
      }
    };
  }

  private void runProcessFiles(final @NotNull FileTreeIterator fileIterator) {
    final Runnable[] resultRunnable = new Runnable[1];

    Runnable readAction = new Runnable() {
      @Override
      public void run() {
        resultRunnable[0] = createIterativeFileProcessor(fileIterator);
      }
    };

    Runnable writeAction = new Runnable() {
      @Override
      public void run() {
        if (resultRunnable[0] != null) {
          resultRunnable[0].run();
        }
      }
    };

    runLayoutCodeProcess(readAction, writeAction, true);
  }

  private static boolean canBeFormatted(PsiFile file) {
    if (LanguageFormatting.INSTANCE.forContext(file) == null) {
      return false;
    }
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return true;

    if (ProjectCoreUtil.isProjectOrWorkspaceFile(virtualFile)) return false;

    for (GeneratedSourcesFilter filter : GeneratedSourcesFilter.EP_NAME.getExtensions()) {
      if (filter.isGeneratedSource(virtualFile, file.getProject())) {
        return false;
      }
    }
    return true;
  }

  private void runLayoutCodeProcess(final Runnable readAction, final Runnable writeAction, final boolean globalAction) {
    final ProgressWindow progressWindow = new ProgressWindow(true, myProject);
    progressWindow.setTitle(myCommandName);
    progressWindow.setText(myProgressText);

    final ModalityState modalityState = ModalityState.current();

    final Runnable process = new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runReadAction(readAction);
      }
    };

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        try {
          ProgressManager.getInstance().runProcess(process, progressWindow);
        }
        catch(ProcessCanceledException e) {
          return;
        }
        catch(IndexNotReadyException e) {
          return;
        }

        final Runnable writeRunnable = new Runnable() {
          @Override
          public void run() {
            CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
              @Override
              public void run() {
                if (globalAction) CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);
                try {
                  ApplicationManager.getApplication().runWriteAction(writeAction);

                  if (myPostRunnable != null) {
                    ApplicationManager.getApplication().invokeLater(myPostRunnable);
                  }
                }
                catch (IndexNotReadyException ignored) {
                }
              }
            }, myCommandName, null);
          }
        };

        if (ApplicationManager.getApplication().isUnitTestMode()) {
          writeRunnable.run();
        }
        else {
          ApplicationManager.getApplication().invokeLater(writeRunnable, modalityState, myProject.getDisposed());
        }
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

  private class ReformatFilesTask implements SequentialTask {
    private SequentialModalProgressTask myCompositeTask;

    private final FileTreeIterator myFileTreeIterator;
    private final FileTreeIterator myCountingIterator;

    private int myTotalFiles = 0;
    private int myFilesProcessed = 0;
    private boolean myStopFormatting;
    private boolean myFilesCountingFinished;

    ReformatFilesTask(@NotNull FileTreeIterator fileIterator) {
      myFileTreeIterator = fileIterator;
      myCountingIterator = new FileTreeIterator(fileIterator);
    }

    @Override
    public void prepare() {
    }

    @Override
    public boolean isDone() {
      return myStopFormatting || !myFileTreeIterator.hasNext();
    }

    private void countingIteration() {
      if (myCountingIterator.hasNext()) {
        myCountingIterator.next();
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
        countingIteration();
        return true;
      }

      updateIndicator(myFilesProcessed);

      if (myFileTreeIterator.hasNext()) {
        PsiFile file = myFileTreeIterator.next();
        myFilesProcessed++;
        if (file.isWritable() && canBeFormatted(file)) {
          performFileProcessing(file);
        }
      }

      return true;
    }

    private void performFileProcessing(@NotNull PsiFile file) {
      FutureTask<Boolean> task = preprocessFile(file, myProcessChangedTextOnly);
      task.run();
      try {
        if (!task.get() || task.isCancelled()) {
          myStopFormatting = true;
        }
      }
      catch (InterruptedException e) {
        LOG.error("Got unexpected exception during formatting", e);
      }
      catch (ExecutionException e) {
        LOG.error("Got unexpected exception during formatting", e);
      }
    }

    private void updateIndicator(int filesProcessed) {
      if (myCompositeTask != null) {
        ProgressIndicator indicator = myCompositeTask.getIndicator();
        if (indicator != null)
          indicator.setFraction((double)filesProcessed / myTotalFiles);
      }
    }

    @Override
    public void stop() {
      myStopFormatting = true;
    }

    public void setCompositeTask(@Nullable SequentialModalProgressTask compositeTask) {
      myCompositeTask = compositeTask;
    }
  }
}
