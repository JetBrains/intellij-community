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
package com.intellij.slicer;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.slicer.forward.SliceFUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class SliceUsage extends UsageInfo2UsageAdapter {
  private final SliceUsage myParent;
  private final AnalysisScope myScope;
  private final PsiSubstitutor mySubstitutor;

  public SliceUsage(@NotNull PsiElement element, @NotNull SliceUsage parent, @NotNull PsiSubstitutor substitutor) {
    super(new UsageInfo(element));
    myParent = parent;
    mySubstitutor = substitutor;
    myScope = parent.myScope;
    assert myScope != null;
  }
  public SliceUsage(@NotNull PsiElement element, @NotNull AnalysisScope scope) {
    super(new UsageInfo(element));
    myParent = null;
    myScope = scope;
    mySubstitutor = PsiSubstitutor.EMPTY;
  }

  public void processChildren(Processor<SliceUsage> processor, final boolean dataFlowToThis) {
    final PsiElement element = getElement();
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    //indicator.setText2("<html><body>Searching for usages of "+ StringUtil.trimStart(SliceManager.getElementDescription(element),"<html><body>")+"</body></html>");
    indicator.checkCanceled();

    final Processor<SliceUsage> uniqueProcessor =
      new CommonProcessors.UniqueProcessor<SliceUsage>(processor, new TObjectHashingStrategy<SliceUsage>() {
        public int computeHashCode(final SliceUsage object) {
          return object.getUsageInfo().hashCode();
        }

        public boolean equals(final SliceUsage o1, final SliceUsage o2) {
          return o1.getUsageInfo().equals(o2.getUsageInfo());
        }
      });

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (dataFlowToThis) {
          SliceUtil.processUsagesFlownDownTo(element, uniqueProcessor, SliceUsage.this, mySubstitutor);
        }
        else {
          SliceFUtil.processUsagesFlownFromThe(element, uniqueProcessor, SliceUsage.this);
        }
      }
    });
  }

  public SliceUsage getParent() {
    return myParent;
  }

  @NotNull
  public AnalysisScope getScope() {
    return myScope;
  }

  SliceUsage copy() {
    PsiElement element = getUsageInfo().getElement();
    return getParent() == null ? new SliceUsage(element, getScope()) : new SliceUsage(element, getParent(),mySubstitutor);
  }

  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

}
