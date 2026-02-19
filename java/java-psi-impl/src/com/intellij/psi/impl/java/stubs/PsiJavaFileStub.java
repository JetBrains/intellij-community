// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.stubs.PsiClassHolderFileStub;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public interface PsiJavaFileStub extends PsiClassHolderFileStub<PsiJavaFile> {
  PsiJavaModule getModule();

  /**
   * @return the package name for this file; returns an empty string for missing or malformed package declaration,
   * which denotes the default package.
   */
  @NotNull String getPackageName();
  LanguageLevel getLanguageLevel();
  boolean isCompiled();

  StubPsiFactory getPsiFactory();

  /** @deprecated override {@link #getPsiFactory()} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  void setPsiFactory(StubPsiFactory factory);
}