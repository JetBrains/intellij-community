/*
 * User: anna
 * Date: 05-Aug-2009
 */
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MoveJavaFileHandler extends MoveFileHandler {
  @Override
  public boolean canProcessElement(PsiFile element) {
    return element instanceof PsiJavaFile && !PsiUtil.isInJspFile(element);
  }

  @Override
  public void prepareMovedFile(PsiFile file, PsiDirectory moveDestination, Map<PsiElement, PsiElement> oldToNewMap) {
    final PsiJavaFile javaFile = (PsiJavaFile)file;
    ChangeContextUtil.encodeContextInfo(javaFile, true);
    for (PsiClass psiClass : javaFile.getClasses()) {
      oldToNewMap.put(psiClass, MoveClassesOrPackagesUtil.doMoveClass(psiClass, moveDestination));
    }
  }

  public List<UsageInfo> findUsages(PsiFile psiFile, PsiDirectory newParent, boolean searchInComments, boolean searchInNonJavaFiles) {
    final List<UsageInfo> result = new ArrayList<UsageInfo>();
    final String qualifiedName = JavaDirectoryService.getInstance().getPackage(newParent).getQualifiedName();
    for (PsiClass aClass : ((PsiJavaFile)psiFile).getClasses()) {
      Collections.addAll(result, MoveClassesOrPackagesUtil.findUsages(aClass, searchInComments, searchInNonJavaFiles,
                                                                      StringUtil.getQualifiedName(qualifiedName, aClass.getName())));
    }
    return result.isEmpty() ? null : result;
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
          reference.bindToElement(newElement);
        }
      }
    }
  }

  @Override
  public void updateMovedFile(PsiFile file) throws IncorrectOperationException {
    ChangeContextUtil.decodeContextInfo(file, null, null);
  }
}