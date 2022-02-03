package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.util.RefactoringConflictsUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

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
            if (statementBase != null && statementBase.isOnDemand() && !isUnderRefactoring(statementBase, directoriesToMove)) {
              usages.add(new RemoveOnDemandImportStatementsUsageInfo(statementBase));
            }
          }
        }
      }
    }
  }

  private static boolean isUnderRefactoring(PsiElement psiElement, PsiDirectory[] directoriesToMove) {
    for (PsiDirectory directory : directoriesToMove) {
      if (PsiTreeUtil.isAncestor(directory, psiElement, true)) {
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
  public void postProcessUsages(UsageInfo[] usages, Function<? super PsiDirectory, ? extends PsiDirectory> newDirMapper) {
    for (UsageInfo usage : usages) {
      if (usage instanceof RemoveOnDemandImportStatementsUsageInfo) {
        final PsiElement element = usage.getElement();
        if (element != null) {
          element.delete();
        }
      }
      if (usage instanceof MoveDirectoryUsageInfo) {
        MoveDirectoryUsageInfo moveDirUsage = (MoveDirectoryUsageInfo)usage;
        PsiDirectory sourceDirectory = moveDirUsage.getSourceDirectory();
        if (sourceDirectory == null) continue;
        PsiJavaModule moduleDescriptor = JavaModuleGraphUtil.findDescriptorByElement(sourceDirectory);
        if (moduleDescriptor == null) continue;

        JavaDirectoryService directoryService = JavaDirectoryService.getInstance();
        PsiPackage oldPackage = directoryService.getPackage(sourceDirectory);
        if (oldPackage == null) continue;
        PsiDirectory targetDirectory = ObjectUtils.tryCast(moveDirUsage.getTargetFileItem(), PsiDirectory.class);
        if (targetDirectory == null) continue;
        PsiPackage newPackage = directoryService.getPackage(targetDirectory);
        if (newPackage == null) continue;

        renamePackageStatements(moduleDescriptor.getExports(), oldPackage, newPackage);
        renamePackageStatements(moduleDescriptor.getOpens(), oldPackage, newPackage);
      }
    }
  }

  private static void renamePackageStatements(@NotNull Iterable<PsiPackageAccessibilityStatement> packageStatements,
                                              @NotNull PsiPackage oldPackage,
                                              @NotNull PsiPackage newPackage) {
    for (PsiPackageAccessibilityStatement exportStatement : packageStatements) {
      PsiJavaCodeReferenceElement packageReference = exportStatement.getPackageReference();
      if (packageReference == null) continue;
      if (!oldPackage.equals(packageReference.resolve())) continue;
      packageReference.bindToElement(newPackage);
      break;
    }
  }

  @Override
  public void preprocessUsages(Project project,
                               Set<PsiFile> files,
                               UsageInfo[] infos,
                               PsiDirectory directory,
                               MultiMap<PsiElement, String> conflicts) {
    RefactoringConflictsUtil.analyzeModuleConflicts(project, files, infos, directory, conflicts);
    if (directory != null) {
      PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
      if (aPackage != null) {
        MoveClassesOrPackagesProcessor.detectPackageLocalsUsed(conflicts, files.toArray(PsiElement.EMPTY_ARRAY), new PackageWrapper(aPackage));
      }
    }
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
    RemoveOnDemandImportStatementsUsageInfo(PsiImportStatementBase statementBase) {
      super(statementBase);
    }
  }
}
