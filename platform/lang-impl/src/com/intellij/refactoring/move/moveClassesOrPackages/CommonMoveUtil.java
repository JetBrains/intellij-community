package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CommonMoveUtil {

  private CommonMoveUtil() {
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveClassesOrPackages.CommonMoveUtil");

  public static NonCodeUsageInfo[] retargetUsages(final UsageInfo[] usages, final Map<PsiElement, PsiElement> oldToNewElementsMapping)
    throws IncorrectOperationException {
    List<NonCodeUsageInfo> nonCodeUsages = new ArrayList<NonCodeUsageInfo>();
    for (UsageInfo usage : usages) {
      if (usage instanceof NonCodeUsageInfo) {
        nonCodeUsages.add((NonCodeUsageInfo)usage);
      }
      else if (usage instanceof MoveRenameUsageInfo) {
        final MoveRenameUsageInfo moveRenameUsage = (MoveRenameUsageInfo)usage;
        final PsiElement oldElement = moveRenameUsage.getReferencedElement();
        final PsiElement newElement = oldToNewElementsMapping.get(oldElement);
        LOG.assertTrue(newElement != null);
        final PsiReference reference = moveRenameUsage.getReference();
        if (reference != null) {
          try {
            reference.bindToElement(newElement);
          }
          catch (IncorrectOperationException e) {//
          }
        }
      }
    }
    return nonCodeUsages.toArray(new NonCodeUsageInfo[nonCodeUsages.size()]);
  }
}
