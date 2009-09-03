package com.intellij.slicer;

import com.intellij.analysis.AnalysisScope;
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

  public void processChildren(Processor<SliceUsage> processor, boolean dataFlowToThis) {
    PsiElement element = getElement();
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    //indicator.setText2("<html><body>Searching for usages of "+ StringUtil.trimStart(SliceManager.getElementDescription(element),"<html><body>")+"</body></html>");
    indicator.checkCanceled();

    Processor<SliceUsage> uniqueProcessor =
      new CommonProcessors.UniqueProcessor<SliceUsage>(processor, new TObjectHashingStrategy<SliceUsage>() {
        public int computeHashCode(final SliceUsage object) {
          return object.getUsageInfo().hashCode();
        }

        public boolean equals(final SliceUsage o1, final SliceUsage o2) {
          return o1.getUsageInfo().equals(o2.getUsageInfo());
        }
      });

    if (dataFlowToThis) {
      SliceUtil.processUsagesFlownDownTo(element, uniqueProcessor, this, mySubstitutor);
    }
    else {
      SliceFUtil.processUsagesFlownFromThe(element, uniqueProcessor, this);
    }
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
