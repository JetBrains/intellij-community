/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.codeInsight.FileModificationService;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public abstract class AbstractLayoutCodeProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor");

  protected final Project myProject;
  private final Module myModule;

  private PsiDirectory myDirectory;
  private PsiFile myFile;
  private PsiFile[] myFiles;
  private boolean myIncludeSubdirs;

  private final String myProgressText;
  private final String myCommandName;
  private final Runnable myPostRunnable;
  private final boolean myProcessChangedTextOnly;

  protected AbstractLayoutCodeProcessor(Project project, String commandName, String progressText, boolean processChangedTextOnly) {
    this(project, (Module)null, commandName, progressText, processChangedTextOnly);
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
    myFiles = filterFiles(files);
    myProgressText = progressText;
    myCommandName = commandName;
    myPostRunnable = postRunnable;
    myProcessChangedTextOnly = processChangedTextOnly;
  }

  private static PsiFile[] filterFiles(PsiFile[] files){
    ArrayList<PsiFile> list = new ArrayList<PsiFile>();
    for (PsiFile file : files) {
      if (isFormatable(file)) {
        list.add(file);
      }
    }
    return PsiUtilCore.toPsiFileArray(list);
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
  protected abstract FutureTask<Boolean> preprocessFile(@NotNull PsiFile file, boolean processChangedTextOnly)
    throws IncorrectOperationException;

  public void run() {
    if (myDirectory != null){
      runProcessDirectory(myDirectory, myIncludeSubdirs);
    }
    else if (myFiles != null){
      runProcessFiles(myFiles);
    }
    else if (myFile != null) {
      runProcessFile(myFile);
    }
    else if (myModule != null) {
      runProcessOnModule(myModule);
    }
    else if (myProject != null) {
      runProcessOnProject(myProject);
    }
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
  private Runnable preprocessFiles(List<PsiFile> files) {
    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    String oldText = null;
    double oldFraction = 0;
    if (progress != null){
      oldText = progress.getText();
      oldFraction = progress.getFraction();
      progress.setText(myProgressText);
    }

    final List<FutureTask<Boolean>> tasks = new ArrayList<FutureTask<Boolean>>(files.size());
    for(int i = 0; i < files.size(); i++) {
      PsiFile file = files.get(i);
      if (progress != null){
        if (progress.isCanceled()) return null;
        progress.setFraction((double)i / files.size());
      }
      if (file.isWritable()){
        try{
          tasks.add(preprocessFile(file, myProcessChangedTextOnly));
        }
        catch(IncorrectOperationException e){
          LOG.error(e);
        }
      }
      files.set(i, null);
    }

    if (progress != null){
      progress.setText(oldText);
      progress.setFraction(oldFraction);
    }

    return new Runnable() {
      @Override
      public void run() {
        SequentialModalProgressTask progressTask = new SequentialModalProgressTask(myProject, myCommandName);
        ReformatFilesTask reformatFilesTask = new ReformatFilesTask(tasks);
        reformatFilesTask.setCompositeTask(progressTask);
        progressTask.setTask(reformatFilesTask);
        ProgressManager.getInstance().run(progressTask);
      }
    };
  }

  private void runProcessFiles(final PsiFile[] files) {
    // let's just ignore read-only files here

    final Runnable[] resultRunnable = new Runnable[1];
    runLayoutCodeProcess(
      new Runnable() {
        @Override
        public void run() {
          resultRunnable[0] = preprocessFiles(new ArrayList<PsiFile>(Arrays.asList(files)));
        }
      },
      new Runnable() {
        @Override
        public void run() {
          if (resultRunnable[0] != null){
            resultRunnable[0].run();
          }
        }
      }, files.length > 1
    );
  }

  private void runProcessDirectory(final PsiDirectory directory, final boolean recursive) {
    final ArrayList<PsiFile> array = new ArrayList<PsiFile>();
    collectFilesToProcess(array, directory, getIgnoreRoots(directory.getProject()), recursive);
    final String where = CodeInsightBundle.message("process.scope.directory", directory.getVirtualFile().getPresentableUrl());
    runProcessOnFiles(where, array);
  }

  private void runProcessOnProject(final Project project) {
    final ArrayList<PsiFile> array = new ArrayList<PsiFile>();
    collectFilesInProject(project, array);
    String where = CodeInsightBundle.message("process.scope.project", project.getPresentableUrl());
    runProcessOnFiles(where, array);
  }

  private void runProcessOnModule(final Module module) {
    final ArrayList<PsiFile> array = new ArrayList<PsiFile>();
    collectFilesInModule(module, array);
    String where = CodeInsightBundle.message("process.scope.module", module.getModuleFilePath());
    runProcessOnFiles(where, array);
  }

  private void collectFilesInProject(Project project, ArrayList<PsiFile> array) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      collectFilesInModule(module, array);
    }
  }

  private void collectFilesInModule(Module module, ArrayList<PsiFile> array) {
    final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    for (VirtualFile root : contentRoots) {
      PsiDirectory dir = PsiManager.getInstance(myProject).findDirectory(root);
      if (dir != null) {
        collectFilesToProcess(array, dir, getIgnoreRoots(module.getProject()), true);
      }
    }
  }

  private void runProcessOnFiles(final String where, final List<PsiFile> array) {
    boolean success = FileModificationService.getInstance().preparePsiElementsForWrite(array);

    if (!success) {
      List<PsiFile> writeables = new ArrayList<PsiFile>();
      for (PsiFile file : array) {
        if (file.isWritable()) {
          writeables.add(file);
        }
      }
      if (writeables.isEmpty()) return;
      int res = Messages.showOkCancelDialog(myProject, CodeInsightBundle.message("error.dialog.readonly.files.message", where),
                                            CodeInsightBundle.message("error.dialog.readonly.files.title"), Messages.getQuestionIcon());
      if (res != 0) {
        return;
      }

      array.clear();
      array.addAll(writeables);
    }

    final Runnable[] resultRunnable = new Runnable[1];
    runLayoutCodeProcess(new Runnable() {
      @Override
      public void run() {
        resultRunnable[0] = preprocessFiles(array);
      }
    }, new Runnable() {
      @Override
      public void run() {
        if (resultRunnable[0] != null) {
          resultRunnable[0].run();
        }
      }
    }, array.size() > 1);
  }

  private static boolean isFormatable(PsiFile file) {
    return LanguageFormatting.INSTANCE.forContext(file) != null;
  }

  /**
   * There is a possible case that 'reformat' is invoked against particular directory via 'Project View'. We don't want
   * to modify project/module files then (these are internal files and their change due to reformatting will trigger dialog
   * that asks user if he or she wants to reload a project because of config file contents change).
   * <p/>
   * This method allows to collect set of file system entries (either files or directories) which contents should be ignored
   * during bulk reformatting.
   *
   * @param project  target project
   * @return         collection of file system entries that shouldn't be touched during bulk reformatting
   */
  private static Set<VirtualFile> getIgnoreRoots(@NotNull Project project) {
    Set<VirtualFile> result = new HashSet<VirtualFile>();

    VirtualFile projectBaseDir = project.getBaseDir();
    if (projectBaseDir != null && projectBaseDir.isDirectory()) {
      VirtualFile projectVirtualDirectory = projectBaseDir.findChild(Project.DIRECTORY_STORE_FOLDER);
      if (projectVirtualDirectory != null) {
        result.add(projectVirtualDirectory);
      }
    }

    VirtualFile projectFile = project.getProjectFile();
    if (projectFile != null) {
      result.add(projectFile);
    }

    VirtualFile workspaceFile = project.getWorkspaceFile();
    if (workspaceFile != null) {
      result.add(workspaceFile);
    }

    for (Module m : ModuleManager.getInstance(project).getModules()) {
      VirtualFile moduleFile = m.getModuleFile();
      if (moduleFile != null) {
        result.add(moduleFile);
      }
    }

    return result;
  }

  private static void collectFilesToProcess(ArrayList<PsiFile> array, PsiDirectory dir, @NotNull Set<VirtualFile> ignoreRoots,
                                            boolean recursive)
  {
    PsiFile[] files = dir.getFiles();
    for (PsiFile file : files) {
      if (isFormatable(file) && !shouldIgnore(file, ignoreRoots)) {
        array.add(file);
      }
    }
    if (recursive){
      PsiDirectory[] subdirs = dir.getSubdirectories();
      for (PsiDirectory subdir : subdirs) {
        collectFilesToProcess(array, subdir, ignoreRoots, recursive);
      }
    }
  }

  private static boolean shouldIgnore(@NotNull PsiFile file, Set<VirtualFile> ignoreRoots) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return false;
    }
    for (VirtualFile root : ignoreRoots) {
      if (VfsUtilCore.isAncestor(root, virtualFile, false)) {
        return true;
      }
    }
    return false;
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
          //DaemonCodeAnalyzer.getInstance(myProject).setUpdateByTimerEnabled(false);
          ProgressManager.getInstance().runProcess(process, progressWindow);
        }
        catch(ProcessCanceledException e) {
          return;
        }
        catch(IndexNotReadyException e) {
          return;
        }
        /*
        finally {
          DaemonCodeAnalyzer.getInstance(myProject).setUpdateByTimerEnabled(true);
        }
        */

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

    private final List<FutureTask<Boolean>> myTasks;
    private final int                       myTotalTasksNumber;

    private SequentialModalProgressTask myCompositeTask;

    ReformatFilesTask(@NotNull List<FutureTask<Boolean>> tasks) {
      myTasks = tasks;
      myTotalTasksNumber = myTasks.size();
    }

    @Override
    public void prepare() {
    }

    @Override
    public boolean isDone() {
      return myTasks.isEmpty();
    }

    @Override
    public boolean iteration() {
      if (myTasks.isEmpty()) {
        return true;
      }
      FutureTask<Boolean> task = myTasks.remove(myTasks.size() - 1);
      if (task == null) {
        return myTasks.isEmpty();
      }
      task.run();
      try {
        if (!task.get() || task.isCancelled()) {
          myTasks.clear();
          return true;
        }
      }
      catch (InterruptedException e) {
        LOG.error("Got unexpected exception during formatting", e);
        return true;
      }
      catch (ExecutionException e) {
        LOG.error("Got unexpected exception during formatting", e);
        return true;
      }
      if (myCompositeTask != null) {
        ProgressIndicator indicator = myCompositeTask.getIndicator();
        if (indicator != null) {
          indicator.setText(myProgressText + (myTotalTasksNumber - myTasks.size()) + "/" + myTotalTasksNumber);
          indicator.setFraction((double)(myTotalTasksNumber - myTasks.size()) / myTotalTasksNumber);
        }
      }
      return myTasks.isEmpty();
    }

    @Override
    public void stop() {
      myTasks.clear();
    }

    public void setCompositeTask(@Nullable SequentialModalProgressTask compositeTask) {
      myCompositeTask = compositeTask;
    }
  }
}
