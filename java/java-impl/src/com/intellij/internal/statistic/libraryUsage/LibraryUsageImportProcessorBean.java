// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiElement;

final class LibraryUsageImportProcessorBean extends LanguageExtension<LibraryUsageImportProcessor<PsiElement>> {
  public static final LibraryUsageImportProcessorBean INSTANCE = new LibraryUsageImportProcessorBean();

  @SuppressWarnings("PublicConstructorInNonPublicClass")
  public LibraryUsageImportProcessorBean() {
    super("com.intellij.internal.statistic.libraryUsageImportProcessor");
  }
}
