// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceUsageInfo;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

final class UsageHolder {
  private final SmartPsiElementPointer myElementPointer;
  private int myUnsafeUsages;
  private int myNonCodeUnsafeUsages;

  UsageHolder(PsiElement element, UsageInfo[] usageInfos) {
    Project project = element.getProject();
    myElementPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(element);

    for (UsageInfo usageInfo : usageInfos) {
      if (!(usageInfo instanceof SafeDeleteReferenceUsageInfo usage)) continue;
      if (usage.getReferencedElement() != element) continue;

      if (!usage.isSafeDelete()) {
        myUnsafeUsages++;
        if (usage.isNonCodeUsage || isInGeneratedCode(usage, project)) {
          myNonCodeUnsafeUsages++;
        }
      }
    }
  }

  private static boolean isInGeneratedCode(SafeDeleteReferenceUsageInfo usage, Project project) {
    VirtualFile file = usage.getVirtualFile();
    return file != null && GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, project);
  }

  public @NotNull @DialogMessage String getDescription() {
    final PsiElement element = myElementPointer.getElement();
    String message = RefactoringBundle.message("0.has.1.usages.that.are.not.safe.to.delete", RefactoringUIUtil.getDescription(element, true), myUnsafeUsages);
    if (myNonCodeUnsafeUsages > 0) {
      message += "<br>" + RefactoringBundle.message("safe.delete.of.those.0.in.comments.strings.non.code", myNonCodeUnsafeUsages);
    }
    return StringUtil.capitalize(message);
  }

  public boolean hasUnsafeUsagesInCode() {
    return myUnsafeUsages != myNonCodeUnsafeUsages;
  }
}
