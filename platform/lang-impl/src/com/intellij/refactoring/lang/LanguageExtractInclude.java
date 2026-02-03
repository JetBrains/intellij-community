// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.lang;

import com.intellij.lang.LanguageExtension;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Internal
public final class LanguageExtractInclude extends LanguageExtension<RefactoringActionHandler> {
  public static final LanguageExtractInclude INSTANCE = new LanguageExtractInclude();

  private LanguageExtractInclude() {
    super("com.intellij.refactoring.extractIncludeHandler");
  }
}
