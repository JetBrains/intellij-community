// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public record CleanupProblems(@NotNull Collection<? extends PsiFile> files,
                              @NotNull List<? extends ProblemDescriptor> problemDescriptors,
                              boolean isGlobalScope) {
}
