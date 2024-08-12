// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.copy;

import com.intellij.CommonBundle;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.ide.CopyPasteDelegator;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.util.PlatformPackageUtil;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
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
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
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
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class CopyFilesOrDirectoriesHandler extends CopyHandlerDelegateBase implements DumbAware {
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

  private static @Nullable PsiDirectory tryNotNullizeDirectory(@NotNull Project project, @Nullable PsiDirectory defaultTargetDirectory) {
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

  private static @Nullable PsiDirectory getCommonParentDirectory(PsiElement[] elements){
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
   * @param newName can be not null only if elements.length == 1
   */
  private static void copyImpl(final VirtualFile @NotNull [] files,
                               final @Nullable String newName,
                               final @NotNull PsiDirectory targetDirectory,
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
      List<PsiFile> originals = new ArrayList<>();
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
      copyToDirectory(items, newName, targetDirectory, choice, title, added, originals);


      if (!added.isEmpty()) {
        DumbService.getInstance(project).completeJustSubmittedTasks();
        updateAddedFiles(added, originals);
        if (openInEditor) {
          PsiFile firstFile = added.get(0);
          CopyHandler.updateSelectionInActiveProjectView(firstFile, project, doClone);
          if (!(firstFile instanceof PsiBinaryFile)) {
            EditorHelper.openInEditor(firstFile, true, true);
          }
        }
      }
    }
    catch (final IncorrectOperationException | IOException ex) {
      Messages.showErrorDialog(project, ex.getMessage(), RefactoringBundle.message("error.title"));
    }
  }

  /**
   * @deprecated it's better to call {@link CopyFilesOrDirectoriesHandler#updateAddedFiles(List, List)} to provide original elements
   */
  @Deprecated
  public static void updateAddedFiles(List<? extends PsiFile> added) {
    updateAddedFiles(added, ContainerUtil.emptyList());
  }

  public static void updateAddedFiles(List<? extends PsiFile> added, @NotNull List<? extends PsiFile> originals) {
    if (added.isEmpty()) return;
    Project project = added.get(0).getProject();
    DumbService dumbService = DumbService.getInstance(project);
    if (Registry.is("run.refactorings.under.progress")) {
      ApplicationManagerEx.getApplicationEx().runWriteActionWithCancellableProgressInDispatchThread(
        RefactoringBundle.message("progress.title.update.added.files"), project, null, pi -> dumbService.runWithAlternativeResolveEnabled(() -> UpdateAddedFileProcessor.updateAddedFiles(added, originals)));
    }
    else {
      WriteAction.run(() -> dumbService.runWithAlternativeResolveEnabled(() -> UpdateAddedFileProcessor.updateAddedFiles(added, originals)));
    }
  }

  /**
   * @param elementToCopy PsiFile or PsiDirectory
   * @param newName can be not null only if elements.length == 1
   * @return first copied PsiFile (recursively); null if no PsiFiles copied
   */
  public static @Nullable PsiFile copyToDirectory(@NotNull PsiFileSystemItem elementToCopy,
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
  public static @Nullable PsiFile copyToDirectory(@NotNull PsiFileSystemItem elementToCopy,
                                        @Nullable String newName,
                                        @NotNull PsiDirectory targetDirectory,
                                        int @Nullable [] choice,
                                        @Nullable @NlsContexts.Command String title) throws IncorrectOperationException, IOException {
    ArrayList<PsiFile> added = new ArrayList<>();
    ArrayList<PsiFile> originals = new ArrayList<>();
    copyToDirectory(Collections.singletonList(elementToCopy), newName, targetDirectory, choice, title, added, originals);
    if (added.isEmpty()) {
      return null;
    }
    DumbService.getInstance(elementToCopy.getProject()).completeJustSubmittedTasks();
    updateAddedFiles(added, originals);
    return added.get(0);
  }

  private static void copyToDirectory(List<? extends PsiFileSystemItem> elementsToCopy,
                                      @Nullable String newName,
                                      @NotNull PsiDirectory targetDirectory,
                                      int @Nullable [] choice,
                                      @Nullable @NlsContexts.Command String title,
                                      @NotNull List<? super PsiFile> added,
                                      @NotNull List<? super PsiFile> originals) throws IncorrectOperationException, IOException {
    MultiMap<PsiDirectory, PsiFile> existingFiles = new MultiMap<>();
    ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    if (Registry.is("run.refactorings.under.progress")) {
      AtomicReference<Throwable> thrown = new AtomicReference<>();
      Consumer<ProgressIndicator> copyAction = pi -> {
        Project project = targetDirectory.getProject();
        int fileCount = ActionUtil.underModalProgress(
          project,
          IdeBundle.message("progress.counting.files"),
          () -> countFiles(elementsToCopy)
        );
        pi.setIndeterminate(fileCount <= 1); // don't show progression when copy-pasting a single file
        try {
          for (PsiFileSystemItem elementToCopy : elementsToCopy) {
            copyToDirectoryUnderProgress(elementToCopy, newName, targetDirectory, added, originals, existingFiles,
                                         Ref.create(0), fileCount, pi);
          }
        }
        catch (Throwable e) {
          thrown.set(e);
        }
      };
      CommandProcessor.getInstance().executeCommand(targetDirectory.getProject(),
                                                    () -> app.runWriteActionWithCancellableProgressInDispatchThread(
                                                      ObjectUtils.notNull(title, RefactoringBundle.message("command.name.copy")),
                                                      targetDirectory.getProject(), null, copyAction), title, null);
      Throwable throwable = thrown.get();
      if (throwable instanceof ProcessCanceledException) {
        //process was canceled, don't proceed with existing files
        return;
      }
      rethrow(throwable);
    }
    else {
      WriteCommandAction.writeCommandAction(targetDirectory.getProject())
        .withName(title)
        .run(() -> {
          for (PsiFileSystemItem elementToCopy : elementsToCopy) {
            copyToDirectoryUnderProgress(elementToCopy, newName, targetDirectory, added, originals, existingFiles, Ref.create(0), -1, null);
          }
        });
    }

    handleExistingFiles(newName, targetDirectory, choice, title, existingFiles, added);
  }

  private static int countFiles(List<? extends PsiFileSystemItem> elements) {
    int fileCount = 0;
    for (PsiFileSystemItem child : elements) {
      fileCount += countFiles(child);
    }
    return fileCount;
  }

  private static int countFiles(PsiFileSystemItem element) {
    if (element instanceof PsiDirectory) {
      return countFiles(ContainerUtil.filterIsInstance(element.getChildren(), PsiFileSystemItem.class));
    }
    return 1;
  }

  private static void rethrow(Throwable throwable) throws IOException {
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
                                          @NotNull List<? super PsiFile> added) {
    SkipOverwriteChoice defaultChoice = choice != null && choice[0] > -1 ? SkipOverwriteChoice.values()[choice[0]] : null;
    try {
      defaultChoice = handleExistingFiles(defaultChoice, choice, newName, targetDirectory, title, existingFiles, added, null);
    }
    finally {
      if (choice != null && defaultChoice != null) {
        choice[0] = defaultChoice.ordinal() % 2;
      }
    }
  }

  private static SkipOverwriteChoice handleExistingFiles(SkipOverwriteChoice defaultChoice,
                                                         int @Nullable [] choice,
                                                         String newName,
                                                         PsiDirectory targetDirectory,
                                                         @NlsContexts.DialogTitle String title,
                                                         @NotNull MultiMap<PsiDirectory, PsiFile> existingFiles,
                                                         @NotNull List<? super PsiFile> added,
                                                         ProgressIndicator progressIndicator) {
    for (PsiDirectory tDirectory : existingFiles.keySet()) {
      Collection<PsiFile> replacementFiles = existingFiles.get(tDirectory);
      for (Iterator<PsiFile> iterator = replacementFiles.iterator(); iterator.hasNext(); ) {
        PsiFile replacement = iterator.next();
        String name = newName == null || tDirectory != targetDirectory ? replacement.getName() : newName;
        PsiFile existing = tDirectory.findFile(name);
        if (existing == null) continue;

        SkipOverwriteChoice userChoice = defaultChoice;
        Project project = targetDirectory.getProject();
        if (userChoice == null) {
          userChoice = SkipOverwriteChoice.askUser(targetDirectory, name, title, choice != null);

          if (userChoice == SkipOverwriteChoice.SKIP_ALL) {
            return userChoice;
          }
          else if (userChoice == SkipOverwriteChoice.OVERWRITE_ALL) {
            Consumer<ProgressIndicator> r = pi -> handleExistingFiles(SkipOverwriteChoice.OVERWRITE_ALL, choice, newName, targetDirectory, title, existingFiles, added, pi);
            if (Registry.is("run.refactorings.under.progress")) {
              CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManagerEx.getApplicationEx().runWriteActionWithCancellableProgressInDispatchThread(title, project, null, r), title, null);
            }
            else {
              r.accept(null);
            }
            return SkipOverwriteChoice.OVERWRITE_ALL;
          }
        }

        iterator.remove();
        ThrowableRunnable<RuntimeException> doCopy = () -> {
          if (progressIndicator != null) {
            progressIndicator.setText2(InspectionsBundle.message("processing.progress.text", existing.getName()));
          }
          existing.delete();
          ((PsiDirectoryImpl)targetDirectory).executeWithUpdatingAddedFilesDisabled(() -> ContainerUtil.addIfNotNull(added, tDirectory.copyFileFrom(name, replacement)));
        };

        if (userChoice == SkipOverwriteChoice.OVERWRITE || userChoice == SkipOverwriteChoice.OVERWRITE_ALL && !Registry.is("run.refactorings.under.progress")) {
          WriteCommandAction.writeCommandAction(project)
            .withName(title)
            .run(doCopy);
        }
        else if (userChoice == SkipOverwriteChoice.OVERWRITE_ALL) {
          doCopy.run();
        }
      }
    }
    return defaultChoice;
  }

  /**
   * @param elementToCopy    PsiFile or PsiDirectory
   * @param newName          can be not null only if elements.length == 1
   * @param added            a collection of files to be updated
   * @param originals        a collection of files which were updated
   * @param existingFiles    a collection of files which already exist in the target
   * @param currentFileCount the number of files that are already copied
   * @param totalFileCount   the total file count or -1 if the copy isn't called under progress
   * @param pi               progress indicator if any
   */
  private static void copyToDirectoryUnderProgress(PsiFileSystemItem elementToCopy,
                                                   @Nullable String newName,
                                                   @NotNull PsiDirectory targetDirectory,
                                                   @NotNull List<? super PsiFile> added,
                                                   List<? super PsiFile> originals,
                                                   MultiMap<PsiDirectory, PsiFile> existingFiles,
                                                   Ref<Integer> currentFileCount,
                                                   int totalFileCount,
                                                   @Nullable ProgressIndicator pi) throws IncorrectOperationException, IOException {

    if (pi != null) {
      pi.setFraction((double) currentFileCount.get() / totalFileCount);
      pi.setText2(InspectionsBundle.message("processing.progress.text", elementToCopy.getName()));
    }
    currentFileCount.set(currentFileCount.get() + 1);
    
    if (elementToCopy instanceof PsiFile file) {
      String name = newName == null ? file.getName() : newName;
      final PsiFile existing = targetDirectory.findFile(name);
      if (existing != null && !existing.equals(file)) {
        existingFiles.putValue(targetDirectory, file);
        return;
      }
      originals.add(file);
      ((PsiDirectoryImpl)targetDirectory).executeWithUpdatingAddedFilesDisabled(() -> ContainerUtil.addIfNotNull(added, targetDirectory.copyFileFrom(name, file)));
    }
    else if (elementToCopy instanceof PsiDirectory directory) {
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
        copyToDirectoryUnderProgress(item, null, subdirectory, added, originals, existingFiles, currentFileCount, totalFileCount, pi);
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

  public static @Nullable PsiDirectory resolveDirectory(@NotNull PsiDirectory defaultTargetDirectory) {
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

  @Override
  public @Nullable String getActionName(PsiElement[] elements) {
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
