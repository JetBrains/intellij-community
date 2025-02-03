// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

public abstract class FixableUsagesRefactoringProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(FixableUsagesRefactoringProcessor.class);

  protected FixableUsagesRefactoringProcessor(Project project) {
    super(project);
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usageInfos) {
    CommonRefactoringUtil.sortDepthFirstRightLeftOrder(usageInfos);
    for (UsageInfo usageInfo : usageInfos) {
      if (usageInfo instanceof FixableUsageInfo) {
        try {
          ((FixableUsageInfo)usageInfo).fixUsage();
        }
        catch (IncorrectOperationException e) {
          LOG.info(e);
        }
      }
    }
  }


  @Override
  protected final UsageInfo @NotNull [] findUsages() {
    final List<FixableUsageInfo> usages = Collections.synchronizedList(new ArrayList<>());
    findUsages(usages);
    return usages.toArray(new FixableUsageInfo[0]);
  }

  protected abstract void findUsages(@NotNull List<? super FixableUsageInfo> usages);

  protected static void checkConflicts(Ref<UsageInfo[]> refUsages, MultiMap<PsiElement, @DialogMessage String> conflicts) {
    for (UsageInfo info : refUsages.get()) {
      final String conflict = ((FixableUsageInfo)info).getConflictMessage();
      if (conflict != null) {
        conflicts.putValue(info.getElement(), conflict);
      }
    }
  }
}
