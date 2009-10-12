/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.refactoring.safeDelete;

import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.SmartPointerManager;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceUsageInfo;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;

import java.util.ArrayList;

/**
 * @author dsl
 */
class UsageHolder {
  private final SmartPsiElementPointer myElementPointer;
  private final SafeDeleteReferenceUsageInfo[] myUsages;
  private int myUnsafeUsages = -1;
  private int myNonCodeUsages = -1;

  public UsageHolder(PsiElement element, UsageInfo[] usageInfos) {
    myElementPointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);

    ArrayList<SafeDeleteReferenceUsageInfo> elementUsages = new ArrayList<SafeDeleteReferenceUsageInfo>();
    for (UsageInfo usageInfo : usageInfos) {
      if (usageInfo instanceof SafeDeleteReferenceUsageInfo) {
        final SafeDeleteReferenceUsageInfo referenceUsageInfo = (SafeDeleteReferenceUsageInfo)usageInfo;
        if (referenceUsageInfo.getReferencedElement() == element) {
          elementUsages.add(referenceUsageInfo);
        }
      }
    }
    myUsages =
    elementUsages.toArray(new SafeDeleteReferenceUsageInfo[elementUsages.size()]);
  }

  public int getNonCodeUsagesNumber() {
    if(myNonCodeUsages < 0) {
      int nonCodeUsages = 0;
      for (SafeDeleteReferenceUsageInfo usage : myUsages) {
        if (usage.isNonCodeUsage) {
          nonCodeUsages++;
        }
      }
      myNonCodeUsages = nonCodeUsages;
    }
    return myNonCodeUsages;
  }

  public int getUnsafeUsagesNumber() {
    if(myUnsafeUsages < 0) {
      int nonSafeUsages = 0;
      for (SafeDeleteReferenceUsageInfo usage : myUsages) {
        if (!usage.isSafeDelete()) {
          nonSafeUsages++;
        }
      }
      myUnsafeUsages = nonSafeUsages;
    }
    return myUnsafeUsages;
  }

  public String getDescription() {
    final int nonCodeUsages = getNonCodeUsagesNumber();
    final int unsafeUsages = getUnsafeUsagesNumber();

    if(unsafeUsages == 0) return null;

    final PsiElement element = myElementPointer.getElement();
    if (unsafeUsages == nonCodeUsages) {
      return RefactoringBundle.message("0.has.1.usages.in.comments.and.strings",
                                       RefactoringUIUtil.getDescription(element, true),
                                       unsafeUsages);
    }

    return RefactoringBundle.message("0.has.1.usages.that.are.not.safe.to.delete.of.those.2",
      RefactoringUIUtil.getDescription(element, true), unsafeUsages, nonCodeUsages);
  }
}
