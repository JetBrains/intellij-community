// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public abstract class SliceUsage extends UsageInfo2UsageAdapter {
  private final SliceUsage myParent;
  public final SliceAnalysisParams params;

  // Store provider here to quickly access it from EDT later
  // since the psi element won't be available at that time
  private final @Nullable SliceLanguageSupportProvider mySliceLanguageSupportProvider;

  public SliceUsage(@NotNull PsiElement element, @NotNull SliceUsage parent) {
    this(element, parent, parent.params);
  }

  protected SliceUsage(@NotNull PsiElement element, @NotNull SliceUsage parent, @NotNull SliceAnalysisParams params) {
    super(new UsageInfo(element));
    myParent = parent;
    this.params = params;
    mySliceLanguageSupportProvider = LanguageSlicing.getProvider(element);
  }

  // root usage
  protected SliceUsage(@NotNull PsiElement element, @NotNull SliceAnalysisParams params) {
    super(new UsageInfo(element));
    myParent = null;
    this.params = params;
    mySliceLanguageSupportProvider = LanguageSlicing.getProvider(element);
  }

  @ApiStatus.Internal
  public @Nullable SliceLanguageSupportProvider getSliceLanguageSupportProvider() {
    return mySliceLanguageSupportProvider;
  }

  private static @NotNull Collection<SliceUsage> transformToLanguageSpecificUsage(@NotNull SliceUsage usage) {
    PsiElement element = usage.getElement();
    if (element == null) return Collections.singletonList(usage);
    SliceLanguageSupportProvider provider = LanguageSlicing.getProvider(element);
    if (!(provider instanceof SliceUsageTransformer)) return Collections.singletonList(usage);
    Collection<SliceUsage> transformedUsages = ((SliceUsageTransformer)provider).transform(usage);
    return transformedUsages != null ? transformedUsages : Collections.singletonList(usage);
  }

  public void processChildren(@NotNull Processor<? super SliceUsage> processor) {
    final PsiElement element = ReadAction.compute(this::getElement);
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    indicator.checkCanceled();

    final HashingStrategy<SliceUsage> strategy = new HashingStrategy<>() {
      @Override
      public int hashCode(SliceUsage object) {
        return object.getUsageInfo().hashCode();
      }

      @Override
      public boolean equals(SliceUsage o1, SliceUsage o2) {
        return o1.getUsageInfo().equals(o2.getUsageInfo());
      }
    };

    final class SliceUsageUniqueProcessor extends CommonProcessors.UniqueProcessor<SliceUsage> {
      private SliceUsageUniqueProcessor(@NotNull Processor<? super SliceUsage> processor, HashingStrategy<? super SliceUsage> strategy) {
        super(processor, strategy);
      }

      @Override
      public boolean process(SliceUsage usage) {
        SliceValueFilter filter = usage.params.valueFilter;
        if (filter != null) {
          PsiElement psiElement = usage.getElement();
          if (psiElement != null && !filter.allowed(psiElement)) {
            return true;
          }
        }
        return ContainerUtil.and(transformToLanguageSpecificUsage(usage), super::process);
      }
    }

    final SliceUsageUniqueProcessor uniqueProcessor = new SliceUsageUniqueProcessor(processor, strategy);

    final Runnable processUsagesFlow = () -> {
      if (params.dataFlowToThis) {
        processUsagesFlownDownTo(element, uniqueProcessor);
      }
      else {
        processUsagesFlownFromThe(element, uniqueProcessor);
      }
    };

    ReadAction.nonBlocking(processUsagesFlow).executeSynchronously();
  }

  protected abstract void processUsagesFlownFromThe(PsiElement element, Processor<? super SliceUsage> uniqueProcessor);

  protected abstract void processUsagesFlownDownTo(PsiElement element, Processor<? super SliceUsage> uniqueProcessor);

  public SliceUsage getParent() {
    return myParent;
  }

  public @NotNull AnalysisScope getScope() {
    return params.scope;
  }

  protected abstract @NotNull SliceUsage copy();

  public boolean canBeLeaf() {
    return getElement() != null;
  }

}
