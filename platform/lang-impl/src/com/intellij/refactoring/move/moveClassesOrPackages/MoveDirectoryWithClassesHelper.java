// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Function;
import com.intellij.util.containers.MultiMap;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author ksafonov
 */
public abstract class MoveDirectoryWithClassesHelper {
  private static final ExtensionPointName<MoveDirectoryWithClassesHelper> EP_NAME =
    ExtensionPointName.create("com.intellij.refactoring.moveDirectoryWithClassesHelper");

  public abstract void findUsages(Collection<? extends PsiFile> filesToMove, PsiDirectory[] directoriesToMove, Collection<? super UsageInfo> result,
                                  boolean searchInComments, boolean searchInNonJavaFiles, Project project);

  public void findUsages(Map<VirtualFile, MoveDirectoryWithClassesProcessor.TargetDirectoryWrapper> filesToMove,
                         PsiDirectory[] directoriesToMove, Collection<? super UsageInfo> result,
                         boolean searchInComments, boolean searchInNonJavaFiles, Project project) {
    Set<PsiFile> psiFiles = filesToMove.keySet().stream().map(PsiManager.getInstance(project)::findFile)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
    findUsages(psiFiles, directoriesToMove, result, searchInComments, searchInNonJavaFiles, project);
  }

  public abstract boolean move(PsiFile file,
                               PsiDirectory moveDestination,
                               Map<PsiElement, PsiElement> oldToNewElementsMapping,
                               List<? super PsiFile> movedFiles,
                               RefactoringElementListener listener);

  public abstract void postProcessUsages(UsageInfo[] usages, Function<? super PsiDirectory, ? extends PsiDirectory> newDirMapper);

  public abstract void beforeMove(PsiFile psiFile);

  public abstract void afterMove(PsiElement newElement);

  public void preprocessUsages(Project project,
                               Set<PsiFile> files,
                               UsageInfo[] infos,
                               PsiDirectory targetDirectory,
                               MultiMap<PsiElement, String> conflicts) {}

  public static List<MoveDirectoryWithClassesHelper> findAll() {
    return EP_NAME.getExtensionList();
  }


  public static class Default extends MoveDirectoryWithClassesHelper {

    @Override
    public void findUsages(Collection<? extends PsiFile> filesToMove,
                           PsiDirectory[] directoriesToMove,
                           Collection<? super UsageInfo> result,
                           boolean searchInComments,
                           boolean searchInNonJavaFiles,
                           Project project) {
      for (PsiFile file : filesToMove) {
        for (PsiReference reference : ReferencesSearch.search(file)) {
          result.add(new MoveDirectoryUsageInfo(reference, file));
        }
      }
      for (PsiDirectory psiDirectory : directoriesToMove) {
        for (PsiReference reference : ReferencesSearch.search(psiDirectory)) {
          result.add(new MoveDirectoryUsageInfo(reference, psiDirectory));
        }
      }
    }

    @Override
    public void postProcessUsages(UsageInfo[] usages, Function<? super PsiDirectory, ? extends PsiDirectory> newDirMapper) {
      for (UsageInfo usage : usages) {
        if (usage instanceof MoveDirectoryUsageInfo) {
          PsiReference reference = usage.getReference();
          if (reference != null) {
            PsiFileSystemItem file = ((MoveDirectoryUsageInfo)usage).getTargetFileItem();
            if (file instanceof PsiDirectory) {
              file = newDirMapper.fun((PsiDirectory)file);
            }
            reference.bindToElement(file);
          }
        }
      }
    }

    @Override
    public boolean move(PsiFile psiFile,
                        PsiDirectory moveDestination,
                        Map<PsiElement, PsiElement> oldToNewElementsMapping,
                        List<? super PsiFile> movedFiles,
                        RefactoringElementListener listener) {
      if (moveDestination.equals(psiFile.getContainingDirectory())) {
        return false;
      }

      MoveFileHandler.forElement(psiFile).prepareMovedFile(psiFile, moveDestination, oldToNewElementsMapping);

      PsiFile moving = moveDestination.findFile(psiFile.getName());
      if (moving == null) {
        MoveFilesOrDirectoriesUtil.doMoveFile(psiFile, moveDestination);
      }
      moving = moveDestination.findFile(psiFile.getName());
      movedFiles.add(moving);
      listener.elementMoved(psiFile);
      return true;
    }

    @Override
    public void beforeMove(PsiFile psiFile) {
    }

    @Override
    public void afterMove(PsiElement newElement) {
    }
  }
}
