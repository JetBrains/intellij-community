// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters.impl;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.HyperlinkInfoFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public final class HyperlinkInfoFactoryImpl extends HyperlinkInfoFactory {

  @Override
  public @NotNull HyperlinkInfo createMultipleFilesHyperlinkInfo(@NotNull List<? extends VirtualFile> files,
                                                                 int line, @NotNull Project project) {
    return new MultipleFilesHyperlinkInfo(files, line, project);
  }

  @Override
  public @NotNull HyperlinkInfo createMultipleFilesHyperlinkInfo(@NotNull List<? extends VirtualFile> files,
                                                                 int line,
                                                                 @NotNull Project project,
                                                                 HyperlinkInfoFactory.@Nullable HyperlinkHandler action) {
    return new MultipleFilesHyperlinkInfo(files, line, project, action);
  }

  @Override
  public @NotNull HyperlinkInfo createMultiplePsiElementHyperlinkInfo(@NotNull Collection<? extends PsiElement> elements) {
    return new MultiPsiElementHyperlinkInfo(elements);
  }
}
