// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MoveJavaFileHandler extends MoveFileHandler {
  private static final Logger LOG = Logger.getInstance(MoveJavaFileHandler.class);

  @Override
  public boolean canProcessElement(PsiFile element) {
    return element instanceof PsiJavaFile &&
           !FileTypeUtils.isInServerPageFile(element) &&
           !JavaProjectRootsUtil.isOutsideJavaSourceRoot(element) &&
           !(element instanceof PsiCompiledElement);
  }

  @Override
  public void prepareMovedFile(PsiFile file, PsiDirectory moveDestination, Map<PsiElement, PsiElement> oldToNewMap) {
    final PsiJavaFile javaFile = (PsiJavaFile)file;
    ChangeContextUtil.encodeContextInfo(javaFile, true);
    for (PsiClass psiClass : javaFile.getClasses()) {
      oldToNewMap.put(psiClass, MoveClassesOrPackagesUtil.doMoveClass(psiClass, moveDestination));
    }
  }

  @Override
  public List<UsageInfo> findUsages(PsiFile psiFile, PsiDirectory newParent, boolean searchInComments, boolean searchInNonJavaFiles) {
    final List<UsageInfo> result = new ArrayList<>();
    final PsiPackage newParentPackage = JavaDirectoryService.getInstance().getPackage(newParent);
    final String qualifiedName = newParentPackage == null ? "" : newParentPackage.getQualifiedName();
    for (PsiClass aClass : ((PsiJavaFile)psiFile).getClasses()) {
      Collections.addAll(result, MoveClassesOrPackagesUtil.findUsages(aClass, searchInComments, searchInNonJavaFiles,
                                                                      StringUtil.getQualifiedName(qualifiedName,
                                                                                                  StringUtil.notNullize(aClass.getName()))));
    }
    return result.isEmpty() ? null : result;
  }

  @Override
  public void detectConflicts(MultiMap<PsiElement, String> conflicts,
                              PsiElement[] elementsToMove,
                              UsageInfo[] usages,
                              PsiDirectory targetDirectory) {
    PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(targetDirectory);
    if (aPackage != null) {
      PackageWrapper packageWrapper = new PackageWrapper(aPackage);
      MoveClassesOrPackagesProcessor.detectConflicts(usages, conflicts, elementsToMove, packageWrapper, new SingleSourceRootMoveDestination(packageWrapper,
                                                                                                                                            targetDirectory));
    }
  }

  @Override
  public void retargetUsages(List<UsageInfo> usageInfos, Map<PsiElement, PsiElement> oldToNewMap) {
    for (UsageInfo usage : usageInfos) {
      if (usage instanceof MoveRenameUsageInfo) {
        final MoveRenameUsageInfo moveRenameUsage = (MoveRenameUsageInfo)usage;
        final PsiElement oldElement = moveRenameUsage.getReferencedElement();
        final PsiElement newElement = oldToNewMap.get(oldElement);
        final PsiReference reference = moveRenameUsage.getReference();
        if (reference != null) {
          try {
            LOG.assertTrue(newElement != null, oldElement != null ? oldElement : reference);
            reference.bindToElement(newElement);
          } catch (IncorrectOperationException ex) {
            LOG.error(ex);
          }
        }
      }
    }
  }

  @Override
  public void updateMovedFile(PsiFile file) throws IncorrectOperationException {
    ChangeContextUtil.decodeContextInfo(file, null, null);
    final PsiDirectory containingDirectory = file.getContainingDirectory();
    if (containingDirectory != null) {
      final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(containingDirectory);
      if (aPackage != null) {
        final String qualifiedName = aPackage.getQualifiedName();
        final PsiNameHelper helper = PsiNameHelper.getInstance(file.getProject());
        final PsiPackageStatement packageStatement = !StringUtil.isEmptyOrSpaces(qualifiedName) && helper.isQualifiedName(qualifiedName)
                                                     ? JavaPsiFacade.getElementFactory(file.getProject()).createPackageStatement(qualifiedName)
                                                     : null;
        if (file instanceof PsiJavaFile) {
          final PsiPackageStatement filePackageStatement = ((PsiJavaFile)file).getPackageStatement();
          if (filePackageStatement != null) {
            if (packageStatement != null) {
              filePackageStatement.getPackageReference().replace(packageStatement.getPackageReference());
            } else {
              filePackageStatement.delete();
            }
          }
        }
      }
    }
  }
}