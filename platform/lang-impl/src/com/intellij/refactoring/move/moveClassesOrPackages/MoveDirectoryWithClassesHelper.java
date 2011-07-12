package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author ksafonov
 */
public abstract class MoveDirectoryWithClassesHelper {
  private static final ExtensionPointName<MoveDirectoryWithClassesHelper> EP_NAME =
    ExtensionPointName.create("com.intellij.refactoring.moveDirectoryWithClassesHelper");

  public abstract void findUsages(Collection<PsiFile> filesToMove, PsiDirectory[] directoriesToMove, Collection<UsageInfo> result,
                                  boolean searchInComments, boolean searchInNonJavaFiles, Project project);

  public abstract boolean move(PsiFile file,
                                  PsiDirectory moveDestination,
                                  Map<PsiElement, PsiElement> oldToNewElementsMapping,
                                  List<PsiFile> movedFiles,
                                  RefactoringElementListener listener);

  public abstract void postProcessUsages(UsageInfo[] usages);

  public abstract void beforeMove(PsiFile psiFile);

  public abstract void afterMove(PsiElement newElement);

  public static MoveDirectoryWithClassesHelper[] findAll() {
    return Extensions.getExtensions(EP_NAME);
  }


  public static class Default extends MoveDirectoryWithClassesHelper {

    @Override
    public void findUsages(Collection<PsiFile> filesToMove,
                           PsiDirectory[] directoriesToMove,
                           Collection<UsageInfo> result,
                           boolean searchInComments,
                           boolean searchInNonJavaFiles,
                           Project project) {
      for (PsiFile file : filesToMove) {
        for (PsiReference reference : ReferencesSearch.search(file)) {
          result.add(new MyUsageInfo(reference, file));
        }
      }
    }

    @Override
    public void postProcessUsages(UsageInfo[] usages) {
      for (UsageInfo usage : usages) {
        if (usage instanceof MyUsageInfo) {
          PsiReference reference = usage.getReference();
          if (reference != null) {
            reference.bindToElement(((MyUsageInfo)usage).myFile);
          }
        }
      }
    }

    @Override
    public boolean move(PsiFile psiFile,
                           PsiDirectory moveDestination,
                           Map<PsiElement, PsiElement> oldToNewElementsMapping,
                           List<PsiFile> movedFiles,
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

    private static class MyUsageInfo extends UsageInfo {
      private final PsiFile myFile;

      public MyUsageInfo(@NotNull PsiReference reference, PsiFile file) {
        super(reference);
        myFile = file;
      }

      @Nullable
      public PsiReference getReference() {
        PsiElement element = getElement();
        if (element == null) {
          return null;
        }
        else {
          final ProperTextRange rangeInElement = getRangeInElement();
          return rangeInElement != null ? element.findReferenceAt(rangeInElement.getStartOffset()) : element.getReference();
        }
      }
    }
  }
}
