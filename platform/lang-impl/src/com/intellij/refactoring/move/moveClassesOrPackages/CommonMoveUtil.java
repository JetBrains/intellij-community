// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class CommonMoveUtil {
  /**
   * Comparator that orders by file and then by element order in the file.
   */
  private static final Comparator<UsageInfo> USAGE_INFO_COMPARATOR = (o1, o2) -> {
    VirtualFile file1 = o1.getVirtualFile();
    VirtualFile file2 = o2.getVirtualFile();
    VirtualFile realFile1 = file1 instanceof VirtualFileWindow window ? window.getDelegate() : file1;
    VirtualFile realFile2 = file2 instanceof VirtualFileWindow window ? window.getDelegate() : file2;
    if (Comparing.equal(realFile1, realFile2)) {
      return getNavigationOffset(file1, o1.getNavigationOffset()) - getNavigationOffset(file2, o2.getNavigationOffset());
    }
    if (realFile1 == null) return -1;
    if (realFile2 == null) return 1;
    return Comparing.compare(realFile1.getPath(), realFile2.getPath());
  };
  
  private static int getNavigationOffset(VirtualFile file, int offset) {
    if (file instanceof VirtualFileWindow window) {
      TextRange range = window.getDocumentWindow().getHostRange(offset);
      return range == null ? offset : range.getStartOffset();
    }
    return offset;
  }

  private static final Logger LOG = Logger.getInstance(CommonMoveUtil.class);

  public static NonCodeUsageInfo[] retargetUsages(UsageInfo @NotNull [] usages, @NotNull Map<PsiElement, PsiElement> oldToNewElementsMapping) {
    Arrays.sort(usages, USAGE_INFO_COMPARATOR);
    List<NonCodeUsageInfo> nonCodeUsages = new ArrayList<>();
    for (UsageInfo usage : usages) {
      if (usage instanceof NonCodeUsageInfo) {
        nonCodeUsages.add((NonCodeUsageInfo)usage);
      }
      else if (usage instanceof MoveRenameUsageInfo moveRenameUsage) {
        final PsiElement oldElement = moveRenameUsage.getReferencedElement();
        final PsiElement newElement = oldToNewElementsMapping.get(oldElement);
        LOG.assertTrue(newElement != null, oldElement);
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
    return nonCodeUsages.toArray(new NonCodeUsageInfo[0]);
  }
}
