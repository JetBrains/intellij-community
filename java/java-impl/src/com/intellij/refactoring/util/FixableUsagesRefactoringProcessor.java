package com.intellij.refactoring.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class FixableUsagesRefactoringProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#" + FixableUsagesRefactoringProcessor.class.getName());

  protected FixableUsagesRefactoringProcessor(Project project) {
    super(project);
  }

  protected void refreshElements(PsiElement[] psiElements) {
  }

  protected void performRefactoring(UsageInfo[] usageInfos) {
    for (UsageInfo usageInfo : usageInfos) {
      try {
        ((FixableUsageInfo)usageInfo).fixUsage();
      }
      catch (IncorrectOperationException e) {
        LOG.info(e);
      }
    }
  }


  @NotNull
  protected final UsageInfo[] findUsages() {
    final List<FixableUsageInfo> usages = new ArrayList<FixableUsageInfo>();
    findUsages(usages);
    final int numUsages = usages.size();
    final FixableUsageInfo[] usageArray = usages.toArray(new FixableUsageInfo[numUsages]);
    RefactoringUtil.sortDepthFirstRightLeftOrder(usageArray);
    return usageArray;
  }

  protected abstract void findUsages(@NotNull List<FixableUsageInfo> usages);

  protected static void checkConflicts(final Ref<UsageInfo[]> refUsages, final List<String> conflicts) {
    for (UsageInfo info : refUsages.get()) {
      final String conflict = ((FixableUsageInfo)info).getConflictMessage();
      if (conflict != null) {
        conflicts.add(XmlUtil.escape(conflict));
      }
    }
  }
}
