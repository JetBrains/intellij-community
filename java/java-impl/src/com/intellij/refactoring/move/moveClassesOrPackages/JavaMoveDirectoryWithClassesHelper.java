package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.util.RefactoringConflictsUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Function;
import com.intellij.util.containers.MultiMap;
import com.intellij.psi.util.FileTypeUtils;

import java.util.*;

public class JavaMoveDirectoryWithClassesHelper extends MoveDirectoryWithClassesHelper {

  @Override
  public void findUsages(Collection<PsiFile> filesToMove,
                         PsiDirectory[] directoriesToMove,
                         Collection<UsageInfo> usages,
                         boolean searchInComments,
                         boolean searchInNonJavaFiles,
                         Project project) {
    final Set<String> packageNames = new HashSet<>();
    for (PsiFile psiFile : filesToMove) {
      if (psiFile instanceof PsiClassOwner) {
        final PsiClass[] classes = ((PsiClassOwner)psiFile).getClasses();
        for (PsiClass aClass : classes) {
          Collections
            .addAll(usages, MoveClassesOrPackagesUtil.findUsages(aClass, searchInComments, searchInNonJavaFiles, aClass.getName()));
        }
        packageNames.add(((PsiClassOwner)psiFile).getPackageName());
      }
    }

    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    for (String packageName : packageNames) {
      final PsiPackage aPackage = psiFacade.findPackage(packageName);
      if (aPackage != null) {
        boolean remainsNothing = true;
        for (PsiDirectory packageDirectory : aPackage.getDirectories()) {
          if (!isUnderRefactoring(packageDirectory, directoriesToMove)) {
            remainsNothing = false;
            break;
          }
        }
        if (remainsNothing) {
          for (PsiReference reference : ReferencesSearch.search(aPackage, GlobalSearchScope.projectScope(project))) {
            final PsiElement element = reference.getElement();
            final PsiImportStatementBase statementBase = PsiTreeUtil.getParentOfType(element, PsiImportStatementBase.class);
            if (statementBase != null && statementBase.isOnDemand()) {
              usages.add(new RemoveOnDemandImportStatementsUsageInfo(statementBase));
            }
          }
        }
      }
    }
  }

  private static boolean isUnderRefactoring(PsiDirectory packageDirectory, PsiDirectory[] directoriesToMove) {
    for (PsiDirectory directory : directoriesToMove) {
      if (PsiTreeUtil.isAncestor(directory, packageDirectory, true)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean move(PsiFile file,
                      PsiDirectory moveDestination,
                      Map<PsiElement, PsiElement> oldToNewElementsMapping,
                      List<PsiFile> movedFiles,
                      RefactoringElementListener listener) {
    if (!(file instanceof PsiClassOwner)) {
      return false;
    }

    final PsiClass[] classes = ((PsiClassOwner)file).getClasses();
    if (classes.length == 0) {
      return false;
    }

    if (FileTypeUtils.isInServerPageFile(file)) {
      return false;
    }

    for (PsiClass psiClass : classes) {
      final PsiClass newClass = MoveClassesOrPackagesUtil.doMoveClass(psiClass, moveDestination);
      oldToNewElementsMapping.put(psiClass, newClass);
      listener.elementMoved(newClass);
    }
    return true;
  }

  @Override
  public void postProcessUsages(UsageInfo[] usages, Function<PsiDirectory, PsiDirectory> newDirMapper) {
    for (UsageInfo usage : usages) {
      if (usage instanceof RemoveOnDemandImportStatementsUsageInfo) {
        final PsiElement element = usage.getElement();
        if (element != null) {
          element.delete();
        }
      }
    }
  }

  @Override
  public void preprocessUsages(Project project,
                               Set<PsiFile> files,
                               UsageInfo[] infos,
                               PsiDirectory directory,
                               MultiMap<PsiElement, String> conflicts) {
    RefactoringConflictsUtil.analyzeModuleConflicts(project, files, infos, directory, conflicts);
  }

  @Override
  public void beforeMove(PsiFile psiFile) {
    ChangeContextUtil.encodeContextInfo(psiFile, true);
  }

  @Override
  public void afterMove(PsiElement newElement) {
    ChangeContextUtil.decodeContextInfo(newElement, null, null);
  }

  private static class RemoveOnDemandImportStatementsUsageInfo extends UsageInfo {
    public RemoveOnDemandImportStatementsUsageInfo(PsiImportStatementBase statementBase) {
      super(statementBase);
    }
  }
}
