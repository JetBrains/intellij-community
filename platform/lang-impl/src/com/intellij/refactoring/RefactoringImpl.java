// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.openapi.util.Ref;
import com.intellij.usageView.UsageInfo;

public abstract class RefactoringImpl<T extends BaseRefactoringProcessor> implements Refactoring {
  protected final T myProcessor;

  public RefactoringImpl(T refactoringProcessor) {
    myProcessor = refactoringProcessor;
  }

  @Override
  public void setPreviewUsages(boolean value) {
    myProcessor.setPreviewUsages(value);
  }

  @Override
  public boolean isPreviewUsages() {
    return myProcessor.isPreviewUsages();
  }

  @Override
  public void setInteractive(Runnable prepareSuccessfulCallback) {
    myProcessor.setPrepareSuccessfulSwingThreadCallback(prepareSuccessfulCallback);
  }

  @Override
  public boolean isInteractive() {
    return myProcessor.getPrepareSuccessfulSwingThreadCallback() != null;
  }

  @Override
  public UsageInfo[] findUsages() {
    return myProcessor.findUsages();
  }

  @Override
  public boolean preprocessUsages(Ref<UsageInfo[]> usages) {
    return myProcessor.preprocessUsages(usages);
  }

  @Override
  public boolean shouldPreviewUsages(UsageInfo[] usages) {
    return myProcessor.isPreviewUsages(usages);
  }

  @Override
  public void doRefactoring(UsageInfo[] usages) {
    myProcessor.execute(usages);
  }

  @Override
  public void run() {
    myProcessor.run();
  }
}
