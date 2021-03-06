// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;

public class PsiElementProcessorAdapter<T extends PsiElement> extends ReadActionProcessor<T> implements Processor<T> {
  private final PsiElementProcessor<? super T> myProcessor;

  public PsiElementProcessorAdapter(final PsiElementProcessor<? super T> processor) {
    myProcessor = processor;
  }

  @Override
  public boolean processInReadAction(final T t) {
    return myProcessor.execute(t);
  }
}