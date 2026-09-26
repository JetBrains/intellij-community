// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.impl.RefactoringTransaction;
import com.intellij.refactoring.move.FileReferenceContextUtil;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesProcessor.TargetDirectoryWrapper;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

/**
 * Represents set of methods that are used in logic of {@link MoveDirectoryWithClassesProcessor}.
 * The methods are stateless and do not show any UI, though they could throw an exception.
 */
@ApiStatus.Internal
public final class MoveDirectoryWithClassesProcessorUtil {
  private MoveDirectoryWithClassesProcessorUtil() {
  }

  public static PsiDirectory[] preprocessDirectories(PsiDirectory[] directories, PsiDirectory targetDirectory) {
    if (targetDirectory != null) {
      final List<PsiDirectory> dirs = new ArrayList<>(Arrays.asList(directories));
      for (Iterator<PsiDirectory> iterator = dirs.iterator(); iterator.hasNext(); ) {
        final PsiDirectory directory = iterator.next();
        if (targetDirectory.equals(directory.getParentDirectory()) || targetDirectory.equals(directory)) {
          iterator.remove();
        }
      }
      directories = dirs.toArray(PsiDirectory.EMPTY_ARRAY);
    }
    return directories;
  }

  static Set<PsiFile> getPsiFiles(Map<VirtualFile, TargetDirectoryWrapper> move, Project project) {
    return move.keySet().stream().map(PsiManager.getInstance(project)::findFile).filter(Objects::nonNull).collect(Collectors.toSet());
  }

  public static UsageInfo @NotNull [] findUsages(
    Project project, Map<VirtualFile, TargetDirectoryWrapper> filesToMove,
    PsiDirectory[] directories,
    boolean searchInComments,
    boolean searchInNonJavaFiles
  ) {
    final List<UsageInfo> usages = new ArrayList<>();
    for (MoveDirectoryWithClassesHelper helper : MoveDirectoryWithClassesHelper.findAll()) {
      helper.findUsages(filesToMove, directories, usages, searchInComments, searchInNonJavaFiles, project);
    }
    return UsageViewUtil.removeDuplicatedUsages(usages.toArray(UsageInfo.EMPTY_ARRAY));
  }

  public static void collectConflicts(@NotNull MultiMap<PsiElement, @DialogMessage String> conflicts,
                                      @NotNull Ref<UsageInfo[]> refUsages,
                                      @NotNull Map<VirtualFile, TargetDirectoryWrapper> myFilesToMove,
                                      @NotNull Project project,
                                      @Nullable PsiDirectory targetDirectory
                                      ) {
    for (VirtualFile vFile : myFilesToMove.keySet()) {
      PsiFile file = PsiManager.getInstance(project).findFile(vFile);
      if (file == null) continue;
      try {
        myFilesToMove.get(vFile).checkMove(file);
      }
      catch (IncorrectOperationException e) {
        conflicts.putValue(file, e.getMessage());
      }
    }
    for (MoveDirectoryWithClassesHelper helper : MoveDirectoryWithClassesHelper.findAll()) {
      helper.preprocessUsages(project, getPsiFiles(myFilesToMove, project), refUsages, targetDirectory, conflicts);
    }
  }

  public static void doPerformRefactoring(Project project,
                                          UsageInfo @NotNull [] usages,
                                          PsiDirectory @NotNull [] directories,
                                          @NotNull Map<PsiDirectory, TargetDirectoryWrapper> nestedDirectoriesToMove,
                                          @NotNull Map<VirtualFile, TargetDirectoryWrapper> filesToMove,
                                          @NotNull RefactoringTransaction transaction,
                                          @NotNull Function<PsiDirectory, TargetDirectoryWrapper> resultDirectoryProvider) {
    //try to create all directories beforehand
    //top level directories should be created even if they are empty
    for (PsiDirectory directory : directories) {
      resultDirectoryProvider.apply(directory).findOrCreateTargetDirectory();
    }

    for (PsiDirectory directory : nestedDirectoriesToMove.keySet()) {
      nestedDirectoriesToMove.get(directory).findOrCreateTargetDirectory();
    }

    for (VirtualFile virtualFile : filesToMove.keySet()) {
      filesToMove.get(virtualFile).findOrCreateTargetDirectory();
    }

    DumbService.getInstance(project).completeJustSubmittedTasks();

    final List<PsiFile> movedFiles = new ArrayList<>();
    final Map<PsiElement, PsiElement> oldToNewElementsMapping = new HashMap<>();
    for (VirtualFile virtualFile : filesToMove.keySet()) {
      PsiFile file = PsiManager.getInstance(project).findFile(virtualFile);
      if (file == null) continue;
      for (MoveDirectoryWithClassesHelper helper : MoveDirectoryWithClassesHelper.findAll()) {
        helper.beforeMove(file);
      }
      final RefactoringElementListener listener = transaction.getElementListener(file);
      final PsiDirectory moveDestination = filesToMove.get(virtualFile).getTargetDirectory();

      for (MoveDirectoryWithClassesHelper helper : MoveDirectoryWithClassesHelper.findAll()) {
        boolean processed = helper.move(file, moveDestination, oldToNewElementsMapping, movedFiles, listener);
        if (processed) {
          break;
        }
      }
    }
    for (PsiElement newElement : oldToNewElementsMapping.values()) {
      for (MoveDirectoryWithClassesHelper helper : MoveDirectoryWithClassesHelper.findAll()) {
        helper.afterMove(newElement);
      }
    }

    // fix references in moved files to outer files
    for (PsiFile movedFile : movedFiles) {
      MoveFileHandler.forElement(movedFile).updateMovedFile(movedFile);
      FileReferenceContextUtil.decodeFileReferences(movedFile);
    }


    List<UsageInfo> usagesToRetarget = new SmartList<>(usages);
    for (MoveDirectoryWithClassesHelper helper : MoveDirectoryWithClassesHelper.findAll()) {
      usagesToRetarget = helper.retargetUsages(usagesToRetarget, oldToNewElementsMapping);
    }
    List<UsageInfo> postProcessUsages = new SmartList<>(usages);
    nestedDirectoriesToMove.entrySet().stream().filter(entry -> entry.getValue().getTargetDirectory() != null)
      .map(entry -> new MoveDirectoryUsageInfo(entry.getKey(), entry.getValue().getTargetDirectory()))
      .forEach(postProcessUsages::add);
    for (MoveDirectoryWithClassesHelper helper : MoveDirectoryWithClassesHelper.findAll()) {
      helper.postProcessUsages(postProcessUsages.toArray(UsageInfo.EMPTY_ARRAY),
                               dir -> resultDirectoryProvider.apply(dir).findOrCreateTargetDirectory());
    }
    for (PsiDirectory directory : directories) {
      if (!isUsedInTarget(directory, nestedDirectoriesToMove)) {
        directory.delete();
      }
    }

    for (PsiDirectory directory : nestedDirectoriesToMove.keySet()) {
      if (directory.isValid() && directory.getChildren().length == 0) {
        directory.delete();
      }
    }
  }

  private static boolean isUsedInTarget(PsiDirectory directory, Map<PsiDirectory, TargetDirectoryWrapper> nestedDirsToMove) {
    PsiDirectory targetDirectory = nestedDirsToMove.get(directory).getTargetDirectory();
    //don't delete super directory if move was performed inside subpackage
    if (targetDirectory != null && PsiTreeUtil.isAncestor(directory, targetDirectory, false)) {
      return true;
    }
    //don't delete subdirectory: something could be moved in there
    if (PsiTreeUtil.isAncestor(targetDirectory, directory, false)) {
      return true;
    }

    return false;
  }

  public static void collectFiles2Move(Map<VirtualFile, TargetDirectoryWrapper> files2Move,
                                        Map<PsiDirectory, TargetDirectoryWrapper> nestedDirsToMove,
                                        PsiDirectory directory,
                                        PsiDirectory rootDirectory,
                                        @NotNull TargetDirectoryWrapper targetDirectory) {
    final PsiElement[] children = directory.getChildren();
    final String relativePath = VfsUtilCore.getRelativePath(directory.getVirtualFile(), rootDirectory.getVirtualFile(), '/');

    final TargetDirectoryWrapper newTargetDirectory = relativePath.isEmpty()
                                                      ? targetDirectory
                                                      : targetDirectory.findOrCreateChild(relativePath);
    nestedDirsToMove.put(directory, newTargetDirectory);
    for (PsiElement child : children) {
      if (child instanceof PsiFile) {
        files2Move.put(PsiUtilCore.getVirtualFile(child), newTargetDirectory);
      }
      else if (child instanceof PsiDirectory){
        collectFiles2Move(files2Move, nestedDirsToMove, (PsiDirectory)child, directory, newTargetDirectory);
      }
    }
  }
}
