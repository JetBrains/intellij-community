// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.compiled;

import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ClsStubBuilder {
  /**
   * Non-zero positive number expected.
   */
  public abstract int getStubVersion();

  /**
   * May return {@code null} for inner or synthetic classes - i.e. those indexed as a part of their parent .class file.
   */
  public abstract @Nullable PsiFileStub<?> buildFileStub(@NotNull FileContent fileContent) throws ClsFormatException;
}
