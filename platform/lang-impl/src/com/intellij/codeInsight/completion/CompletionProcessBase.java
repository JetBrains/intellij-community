// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.patterns.ElementPattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.function.Supplier;

/**
 * @author yole
 */
public class CompletionProcessBase implements CompletionProcessEx, Disposable {
  protected final int myInvocationCount;
  protected final Object myLock = new String("CompletionProgressIndicator");
  protected OffsetsInFile myHostOffsets;
  private CompletionParameters myParameters;

  public CompletionProcessBase(CompletionInitializationContext context) {
    myInvocationCount = context.getInvocationCount();
    myHostOffsets = ((CompletionInitializationContextImpl) context).getHostOffsets();
  }

  @Override
  public boolean isAutopopupCompletion() {
    return myInvocationCount == 0;
  }

  @Override
  public OffsetsInFile getHostOffsets() {
    return myHostOffsets;
  }

  @Override
  public void registerChildDisposable(@NotNull Supplier<Disposable> child) {
    synchronized (myLock) {
      // avoid registering stuff on an indicator being disposed concurrently
      Disposer.register(this, child.get());
    }
  }

  @Override
  public void dispose() {
  }

  @Override
  public void itemSelected(LookupElement item, char aChar) {
  }

  @Override
  public void addWatchedPrefix(int startOffset, ElementPattern<String> restartCondition) {
  }

  @Override
  public void addAdvertisement(String message, @Nullable Color bgColor) {
  }

  @Override
  public CompletionParameters getParameters() {
    return myParameters;
  }

  @Override
  public void setParameters(CompletionParameters parameters) {
    myParameters = parameters;
  }
}
