/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyWithDefaultValue;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author cdr
 */
public class SliceUsage extends UsageInfo2UsageAdapter implements UserDataHolder {
  private final SliceUsage myParent;
  public final SliceAnalysisParams params;
  private Map<Object, Object> myUserData = null;

  public SliceUsage(@NotNull PsiElement element, @NotNull SliceUsage parent) {
    super(new UsageInfo(element));
    myParent = parent;
    params = parent.params;
    assert params != null;
  }

  // root usage
  private SliceUsage(@NotNull PsiElement element, @NotNull SliceAnalysisParams params) {
    super(new UsageInfo(element));
    myParent = null;
    this.params = params;
  }

  @NotNull
  public static SliceUsage createRootUsage(@NotNull PsiElement element, @NotNull SliceAnalysisParams params) {
    return new SliceUsage(element, params);
  }

  public void processChildren(@NotNull final Processor<SliceUsage> processor) {
    final PsiElement element = ApplicationManager.getApplication().runReadAction(new Computable<PsiElement>() {
      @Override
      public PsiElement compute() {
        return getElement();
      }
    });
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    indicator.checkCanceled();

    final Processor<SliceUsage> uniqueProcessor =
      new CommonProcessors.UniqueProcessor<SliceUsage>(processor, new TObjectHashingStrategy<SliceUsage>() {
        @Override
        public int computeHashCode(final SliceUsage object) {
          return object.getUsageInfo().hashCode();
        }

        @Override
        public boolean equals(final SliceUsage o1, final SliceUsage o2) {
          return o1.getUsageInfo().equals(o2.getUsageInfo());
        }
      });

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        final SliceProvider provider = SliceProvider.forElement(element);
        if (params.dataFlowToThis) {
          provider.processUsagesFlownDownTo(element, uniqueProcessor, SliceUsage.this);
        }
        else {
          provider.processUsagesFlownFromThe(element,uniqueProcessor, SliceUsage.this);
        }
      }
    });
  }

  public SliceUsage getParent() {
    return myParent;
  }

  @NotNull
  public AnalysisScope getScope() {
    return params.scope;
  }

  @NotNull
  SliceUsage copy() {
    PsiElement element = getUsageInfo().getElement();
    final SliceUsage usage = getParent() == null ? createRootUsage(element, params) : new SliceUsage(element, getParent());
    if (myUserData != null) {
      usage.myUserData = new HashMap<Object, Object>(myUserData);
    }
    return usage;
  }

  @Override
  public <T> T getUserData(@NotNull final Key<T> key) {
    final T result;
    if (myUserData != null) {
      //noinspection unchecked
      result = (T)myUserData.get(key);
    }
    else {
      result = null;
    }
    if (result == null && key instanceof KeyWithDefaultValue) {
      return ((KeyWithDefaultValue<T>)key).getDefaultValue();
    }
    return result;
  }

  @Override
  public <T> void putUserData(@NotNull final Key<T> key, @Nullable final T value) {
    if (myUserData == null) {
      myUserData = ContainerUtil.newHashMap();
    }
    myUserData.put(key, value);
  }
}
