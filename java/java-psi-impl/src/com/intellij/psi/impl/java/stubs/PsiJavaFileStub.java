// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.stubs.PsiClassHolderFileStub;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author max
 */
public interface PsiJavaFileStub extends PsiClassHolderFileStub<PsiJavaFile> {
  PsiJavaModule getModule();

  String getPackageName();
  LanguageLevel getLanguageLevel();
  boolean isCompiled();

  StubPsiFactory getPsiFactory();

  /** @deprecated override {@link #getPsiFactory()} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  void setPsiFactory(StubPsiFactory factory);
}