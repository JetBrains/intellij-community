/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class FixableUsagesRefactoringProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#" + FixableUsagesRefactoringProcessor.class.getName());

  protected FixableUsagesRefactoringProcessor(Project project) {
    super(project);
  }

  protected void performRefactoring(@NotNull UsageInfo[] usageInfos) {
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


  @NotNull
  protected final UsageInfo[] findUsages() {
    final List<FixableUsageInfo> usages = Collections.synchronizedList(new ArrayList<FixableUsageInfo>());
    findUsages(usages);
    final int numUsages = usages.size();
    final FixableUsageInfo[] usageArray = usages.toArray(new FixableUsageInfo[numUsages]);
    return usageArray;
  }

  protected abstract void findUsages(@NotNull List<FixableUsageInfo> usages);

  protected static void checkConflicts(final Ref<UsageInfo[]> refUsages, final MultiMap<PsiElement,String> conflicts) {
    for (UsageInfo info : refUsages.get()) {
      final String conflict = ((FixableUsageInfo)info).getConflictMessage();
      if (conflict != null) {
        conflicts.putValue(info.getElement(), XmlUtil.escape(conflict));
      }
    }
  }
}
