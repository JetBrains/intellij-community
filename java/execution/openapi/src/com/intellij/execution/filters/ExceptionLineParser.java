// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parser for single exception line
 */
public interface ExceptionLineParser {
  default Filter.Result execute(@NotNull String line, final int textEndOffset) {
    return execute(line, textEndOffset, null);
  }

  Filter.Result execute(@NotNull String line, final int textEndOffset, @Nullable ExceptionLineRefiner elementMatcher);

  ExceptionLineRefiner getLocationRefiner();

  Project getProject();

  Filter.Result getResult();

  PsiClass getPsiClass();

  String getMethod();

  @RequiresReadLock
  @Nullable PsiFile getFile();

  ExceptionWorker.ParsedLine getInfo();
}
