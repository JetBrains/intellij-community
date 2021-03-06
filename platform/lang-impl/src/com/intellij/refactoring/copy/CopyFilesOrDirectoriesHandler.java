// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.copy;

import com.intellij.CommonBundle;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.ide.CopyPasteDelegator;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.util.PlatformPackageUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.impl.FileChooserUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.impl.file.PsiDirectoryImpl;
import com.intellij.psi.impl.file.UpdateAddedFileProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.SkipOverwriteChoice;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class CopyFilesOrDirectoriesHandler extends CopyHandlerDelegateBase {
  private static final Logger LOG = Logger.getInstance(CopyFilesOrDirectoriesHandler.class);

  @Override
  public boolean canCopy(PsiElement[] elements, boolean fromUpdate) {
    Set<String> names = new HashSet<>();
    for (PsiElement element : elements) {
      if (!(element instanceof PsiDirectory || element instanceof PsiFile)) return false;
      if (!element.isValid()) return false;
      if (element instanceof PsiCompiledFile) return false;

      String name = ((PsiFileSystemItem) element).getName();
      if (names.contains(name)) {
        return false;
      }
      names.add(name);
    }
    if (fromUpdate) return elements.length > 0;
    PsiElement[] filteredElements = PsiTreeUtil.filterAncestors(elements);
    return filteredElements.length == elements.length;
  }

  @Override
  public void doCopy(final PsiElement[] elements, PsiDirectory defaultTargetDirectory) {
    if (defaultTargetDirectory == null) {
      PsiDirectory commonParent = getCommonParentDirectory(elements);
      if (commonParent != null && !ScratchUtil.isScratch(commonParent.getVirtualFile())) {
        defaultTargetDirectory = commonParent;
      }
    }
    Project project = defaultTargetDirectory != null ? defaultTargetDirectory.getProject() : elements [0].getProject();
    if (defaultTargetDirectory != null) {
      defaultTargetDirectory = resolveDirectory(defaultTargetDirectory);
      if (defaultTargetDirectory == null) return;
    }

    defaultTargetDirectory = tryNotNullizeDirectory(project, defaultTargetDirectory);

    copyAsFiles(elements, defaultTargetDirectory, project);
  }

  @Nullable
  private static PsiDirectory tryNotNullizeDirectory(@NotNull Project project, @Nullable PsiDirectory defaultTargetDirectory) {
    if (defaultTargetDirectory == null) {
      VirtualFile root = FileChooserUtil.getLastOpenedFile(project);
      if (root == null) root = project.isDefault() ? null : ProjectUtil.guessProjectDir(project);
      if (root == null) root = VfsUtil.getUserHomeDir();
      defaultTargetDirectory = root == null ? null :
                               root.isDirectory() ? PsiManager.getInstance(project).findDirectory(root) :
                               PsiManager.getInstance(project).findDirectory(root.getParent());

      if (defaultTargetDirectory == null) {
        LOG.warn("No directory found for project: " + project.getName() +", root: " + root);
      }
    }
    return defaultTargetDirectory;
  }

  public static void copyAsFiles(PsiElement[] elements, @Nullable PsiDirectory defaultTargetDirectory, Project project) {
    doCopyAsFiles(elements, defaultTargetDirectory, project);
  }

  private static void doCopyAsFiles(PsiElement[] elements, @Nullable PsiDirectory defaultTargetDirectory, Project project) {
    PsiDirectory targetDirectory;
    String newName;
    boolean openInEditor;
    VirtualFile[] files = Arrays.stream(elements).map(el -> ((PsiFileSystemItem)el).getVirtualFile()).toArray(VirtualFile[]::new);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      targetDirectory = defaultTargetDirectory;
      newName = null;
      openInEditor = true;
    }
    else {
      CopyFilesOrDirectoriesDialog dialog = new CopyFilesOrDirectoriesDialog(elements, defaultTargetDirectory, project, false);
      if (dialog.showAndGet()) {
        newName = elements.length == 1 ? dialog.getNewName() : null;
        targetDirectory = dialog.getTargetDirectory();
        openInEditor = dialog.isOpenInEditor();
      }
      else {
        return;
      }
    }

    if (targetDirectory != null) {
      PsiManager manager = PsiManager.getInstance(project);
      try {
        for (VirtualFile file : files) {
          if (file.isDirectory()) {
            PsiFileSystemItem psiElement = manager.findDirectory(file);
            MoveFilesOrDirectoriesUtil.checkIfMoveIntoSelf(psiElement, targetDirectory);
          }
        }
      }
      catch (IncorrectOperationException e) {
        CommonRefactoringUtil.showErrorHint(project, null, e.getMessage(), CommonBundle.getErrorTitle(), null);
        return;
      }

      CommandProcessor.getInstance().executeCommand(project, () -> copyImpl(files, newName, targetDirectory, false, openInEditor),
                                                    RefactoringBundle.message("copy.handler.copy.files.directories"), null);

    }
  }

  @Override
  public void doClone(final PsiElement element) {
    doCloneFile(element);
  }

  public static void doCloneFile(PsiElement element) {
    PsiDirectory targetDirectory;
    if (element instanceof PsiDirectory) {
      targetDirectory = ((PsiDirectory)element).getParentDirectory();
    }
    else {
      targetDirectory = PlatformPackageUtil.getDirectory(element);
    }
    targetDirectory = tryNotNullizeDirectory(element.getProject(), targetDirectory);
    if (targetDirectory == null) return;

    PsiElement[] elements = {element};
    VirtualFile file = ((PsiFileSystemItem)element).getVirtualFile();
    CopyFilesOrDirectoriesDialog dialog = new CopyFilesOrDirectoriesDialog(elements, null, element.getProject(), true);
    if (dialog.showAndGet()) {
      String newName = dialog.getNewName();
      copyImpl(new VirtualFile[] {file}, newName, targetDirectory, true, true);
    }
  }

  @Nullable
  private static PsiDirectory getCommonParentDirectory(PsiElement[] elements){
    PsiDirectory result = null;

    for (PsiElement element : elements) {
      PsiDirectory directory;

      if (element instanceof PsiDirectory) {
        directory = (PsiDirectory)element;
        directory = directory.getParentDirectory();
      }
      else if (element instanceof PsiFile) {
        directory = PlatformPackageUtil.getDirectory(element);
      }
      else {
        throw new IllegalArgumentException("unexpected element " + element);
      }

      if (directory == null) continue;

      if (result == null) {
        result = directory;
      }
      else {
        if (PsiTreeUtil.isAncestor(directory, result, true)) {
          result = directory;
        }
      }
    }

    return result;
  }

  /**
   * @param files
   * @param newName can be not null only if elements.length == 1
   * @param targetDirectory
   * @param openInEditor
   */
  private static void copyImpl(final VirtualFile @NotNull [] files,
                               @Nullable final String newName,
                               @NotNull final PsiDirectory targetDirectory,
                               final boolean doClone,
                               final boolean openInEditor) {
    if (doClone && files.length != 1) {
      throw new IllegalArgumentException("invalid number of elements to clone:" + files.length);
    }

    if (newName != null && files.length != 1) {
      throw new IllegalArgumentException("no new name should be set; number of elements is: " + files.length);
    }

    final Project project = targetDirectory.getProject();
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, Collections.singleton(targetDirectory), true)) {
      return;
    }

    String title = RefactoringBundle.message(doClone ? "copy.handler.clone.files.directories" : "copy.handler.copy.files.directories");
    try {
      final int[] choice = files.length > 1 || files[0].isDirectory() ? new int[]{-1} : null;
      List<PsiFile> added = new ArrayList<>();
      PsiManager manager = PsiManager.getInstance(project);
      List<PsiFileSystemItem> items = new ArrayList<>(files.length);
      for (VirtualFile file : files) {
        PsiFileSystemItem item = file.isDirectory() ? manager.findDirectory(file) : manager.findFile(file);
        if (item == null) {
          LOG.info("invalid file: " + file.getExtension());
          continue;
        }
        items.add(item);
      }
      copyToDirectory(items, newName, targetDirectory, choice, title, added);


      if (!added.isEmpty()) {
        DumbService.getInstance(project).completeJustSubmittedTasks();
        WriteAction.run(() -> UpdateAddedFileProcessor.updateAddedFiles(added));
        if (openInEditor) {
          PsiFile firstFile = added.get(0);
          CopyHandler.updateSelectionInActiveProjectView(firstFile, project, doClone);
          if (!(firstFile instanceof PsiBinaryFile)) {
            EditorHelper.openInEditor(firstFile);
            ToolWindowManager.getInstance(project).activateEditorComponent();
          }
        }
      }
    }
    catch (final IncorrectOperationException | IOException ex) {
      Messages.showErrorDialog(project, ex.getMessage(), RefactoringBundle.message("error.title"));
    }
  }

  /**
   * @param elementToCopy PsiFile or PsiDirectory
   * @param newName can be not null only if elements.length == 1
   * @return first copied PsiFile (recursively); null if no PsiFiles copied
   */
  @Nullable
  public static PsiFile copyToDirectory(@NotNull PsiFileSystemItem elementToCopy,
                                        @Nullable String newName,
                                        @NotNull PsiDirectory targetDirectory) throws IncorrectOperationException, IOException {
    return copyToDirectory(elementToCopy, newName, targetDirectory, null, null);
  }

  /**
   * @param elementToCopy PsiFile or PsiDirectory
   * @param newName can be not null only if elements.length == 1
   * @param choice a horrible way to pass/keep user preference
   * @return first copied PsiFile (recursively); null if no PsiFiles copied
   */
  @Nullable
  public static PsiFile copyToDirectory(@NotNull PsiFileSystemItem elementToCopy,
                                        @Nullable String newName,
                                        @NotNull PsiDirectory targetDirectory,
                                        int @Nullable [] choice,
                                        @Nullable @NlsContexts.Command String title) throws IncorrectOperationException, IOException {
    ArrayList<PsiFile> added = new ArrayList<>();
    copyToDirectory(Collections.singletonList(elementToCopy), newName, targetDirectory, choice, title, added);
    if (added.isEmpty()) {
      return null;
    }
    DumbService.getInstance(elementToCopy.getProject()).completeJustSubmittedTasks();
    WriteAction.run(() -> UpdateAddedFileProcessor.updateAddedFiles(added));
    return added.get(0);
  }

  private static void copyToDirectory(List<PsiFileSystemItem> elementsToCopy,
                                      @Nullable String newName,
                                      @NotNull PsiDirectory targetDirectory,
                                      int @Nullable [] choice,
                                      @Nullable @NlsContexts.Command String title,
                                      @NotNull List<PsiFile> added) throws IncorrectOperationException, IOException {
    MultiMap<PsiDirectory, PsiFile> existingFiles = new MultiMap<>();
    ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    if (Registry.is("run.refactorings.under.progress")) {
      AtomicReference<Throwable> thrown = new AtomicReference<>();
      Consumer<ProgressIndicator> copyAction = pi -> {
        try {
          for (PsiFileSystemItem elementToCopy : elementsToCopy) {
            copyToDirectoryUnderProgress(elementToCopy, newName, targetDirectory, added, existingFiles, pi);
          }
        }
        catch (Throwable e) {
          thrown.set(e);
        }
      };
      app.runWriteActionWithCancellableProgressInDispatchThread(ObjectUtils.notNull(title, RefactoringBundle.message("command.name.copy")), 
                                                                targetDirectory.getProject(), null, copyAction);
      rethrow(thrown.get());
    }
    else {
      WriteCommandAction.writeCommandAction(targetDirectory.getProject())
        .withName(title)
        .run(() -> {
          for (PsiFileSystemItem elementToCopy : elementsToCopy) {
            copyToDirectoryUnderProgress(elementToCopy, newName, targetDirectory, added, existingFiles, null);
          }
        });
    }

    handleExistingFiles(newName, targetDirectory, choice, title, existingFiles, added);
  }

  private static void rethrow(Throwable throwable) throws IOException {
    if (throwable instanceof ProcessCanceledException) {
      return;
    }

    if (throwable instanceof IOException) {
      throw (IOException)throwable;
    }
    else if (throwable instanceof IncorrectOperationException) {
      throw (IncorrectOperationException)throwable;
    }
    else if (throwable != null) {
      throw new IncorrectOperationException(throwable);
    }
  }

  private static void handleExistingFiles(String newName,
                                          PsiDirectory targetDirectory,
                                          int @Nullable [] choice,
                                          @NlsContexts.DialogTitle String title,
                                          @NotNull MultiMap<PsiDirectory, PsiFile> existingFiles,
                                          @NotNull List<PsiFile> added) {
    SkipOverwriteChoice defaultChoice = choice != null && choice[0] > -1 ? SkipOverwriteChoice.values()[choice[0]] : null;
    try {
      for (PsiDirectory tDirectory : existingFiles.keySet()) {
        Collection<PsiFile> replacementFiles = existingFiles.get(tDirectory);
        for (PsiFile replacement : replacementFiles) {
          String name = newName == null || tDirectory != targetDirectory ? replacement.getName() : newName;
          PsiFile existing = tDirectory.findFile(name);
          if (existing == null) continue;
  
          SkipOverwriteChoice userChoice = defaultChoice;
          if (userChoice == null) {
            userChoice = SkipOverwriteChoice.askUser(targetDirectory, name, title, choice != null);

            if (userChoice == SkipOverwriteChoice.SKIP_ALL || userChoice == SkipOverwriteChoice.OVERWRITE_ALL) {
              defaultChoice = userChoice;
            }
          }
  
          switch (userChoice) {
            case OVERWRITE:
            case OVERWRITE_ALL:
              WriteCommandAction.writeCommandAction(targetDirectory.getProject())
                .withName(title)
                .run(() -> {
                  existing.delete();
                  ((PsiDirectoryImpl)targetDirectory).executeWithUpdatingAddedFilesDisabled(() -> ContainerUtil.addIfNotNull(added, tDirectory.copyFileFrom(name, replacement)));
                });
              break;
            case SKIP_ALL:
              return;
          }
        }
      }
    }
    finally {
      if (choice != null && defaultChoice != null) {
        choice[0] = defaultChoice.ordinal() % 2;
      }
    }
  }

  /**
   * @param elementToCopy PsiFile or PsiDirectory
   * @param newName can be not null only if elements.length == 1
   * @param added  a collection of files to be updated
   * @param existingFiles a collection of files which already exist in the target
   * @param pi progress indicator if any
   */
  private static void copyToDirectoryUnderProgress(PsiFileSystemItem elementToCopy,
                                                   @Nullable String newName,
                                                   @NotNull PsiDirectory targetDirectory,
                                                   @NotNull List<PsiFile> added,
                                                   MultiMap<PsiDirectory, PsiFile> existingFiles,
                                                   @Nullable ProgressIndicator pi) throws IncorrectOperationException, IOException {
    if (pi != null) {
      pi.setText2(InspectionsBundle.message("processing.progress.text", elementToCopy.getName()));
    }
    
    if (elementToCopy instanceof PsiFile) {
      PsiFile file = (PsiFile)elementToCopy;
      String name = newName == null ? file.getName() : newName;
      final PsiFile existing = targetDirectory.findFile(name);
      if (existing != null && !existing.equals(file)) {
        existingFiles.putValue(targetDirectory, file);
        return;
      }
      ((PsiDirectoryImpl)targetDirectory).executeWithUpdatingAddedFilesDisabled(() -> ContainerUtil.addIfNotNull(added, targetDirectory.copyFileFrom(name, file)));
    }
    else if (elementToCopy instanceof PsiDirectory) {
      PsiDirectory directory = (PsiDirectory)elementToCopy;
      if (directory.equals(targetDirectory)) {
        return;
      }
      if (newName == null) newName = directory.getName();
      final PsiDirectory existing = targetDirectory.findSubdirectory(newName);
      final PsiDirectory subdirectory;
      if (existing == null) {
        subdirectory = targetDirectory.createSubdirectory(newName);
      }
      else {
        subdirectory = existing;
      }
      EncodingRegistry.doActionAndRestoreEncoding(directory.getVirtualFile(),
                                                  (ThrowableComputable<VirtualFile, IOException>)() -> subdirectory.getVirtualFile());

      VirtualFile[] children = directory.getVirtualFile().getChildren();
      Project project = subdirectory.getProject();
      PsiManager manager = PsiManager.getInstance(project);
      for (VirtualFile file : children) {
        PsiFileSystemItem item = file.isDirectory() ? manager.findDirectory(file) : manager.findFile(file);
        if (item == null) {
          LOG.info("invalid file: " + file.getExtension());
          continue;
        }
        copyToDirectoryUnderProgress(item, null, subdirectory, added, existingFiles, pi);
      }
    }
    else {
      throw new IllegalArgumentException("unexpected elementToCopy: " + elementToCopy);
    }
  }

  public static boolean checkFileExist(@Nullable PsiDirectory targetDirectory, int[] choice, PsiFile file, @NlsSafe String name, @NlsContexts.Command String title) {
    if (targetDirectory == null) return false;
    final PsiFile existing = targetDirectory.findFile(name);
    if (existing != null && !existing.equals(file)) {
      int selection;
      if (choice == null || choice[0] == -1) {
        selection = SkipOverwriteChoice.askUser(targetDirectory, name, title, choice != null).ordinal();
      }
      else {
        selection = choice[0];
      }

      if (choice != null && selection > 1) {
        choice[0] = selection % 2;
        selection = choice[0];
      }

      if (selection == 0) {
        WriteCommandAction.writeCommandAction(targetDirectory.getProject())
          .withName(title)
          .run(() -> existing.delete());
      }
      else {
        return true;
      }
    }

    return false;
  }

  @Nullable
  public static PsiDirectory resolveDirectory(@NotNull PsiDirectory defaultTargetDirectory) {
    final Project project = defaultTargetDirectory.getProject();
    final Boolean showDirsChooser = defaultTargetDirectory.getCopyableUserData(CopyPasteDelegator.SHOW_CHOOSER_KEY);
    if (showDirsChooser != null && showDirsChooser.booleanValue()) {
      final PsiDirectoryContainer directoryContainer =
        PsiDirectoryFactory.getInstance(project).getDirectoryContainer(defaultTargetDirectory);
      if (directoryContainer == null) {
        return defaultTargetDirectory;
      }
      return MoveFilesOrDirectoriesUtil.resolveToDirectory(project, directoryContainer);
    }
    return defaultTargetDirectory;
  }

  @Nullable
  @Override
  public String getActionName(PsiElement[] elements) {
    int fileCount = 0, directoryCount = 0;
    for (PsiElement element : elements) {
      if (element instanceof PsiFile) {
        fileCount++;
      }
      else if (element instanceof PsiDirectory) {
        directoryCount++;
      }
    }
    if (directoryCount == 0) {
      return fileCount == 1 ? RefactoringBundle.message("copy.file") : RefactoringBundle.message("copy.files");
    }
    if (fileCount == 0) {
      return directoryCount == 1 ? RefactoringBundle.message("copy.directory") : RefactoringBundle.message("copy.directories");
    }
    return RefactoringBundle.message("copy.files.and.directories");
  }
}
