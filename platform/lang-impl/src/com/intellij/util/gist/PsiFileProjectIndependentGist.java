// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.gist;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class PsiFileProjectIndependentGist<Data> extends PsiFileGistImpl<Data> {

  PsiFileProjectIndependentGist(@NotNull String id,
                                int version,
                                @NotNull DataExternalizer<Data> externalizer,
                                @NotNull NullableFunction<? super PsiFile, ? extends Data> calculator) {
    super(id, version, externalizer, calculator);
  }

  @Override
  protected @Nullable PsiFile getPsiFile(@Nullable Project project, @NotNull VirtualFile file) {
    if (project == null) {
      project = ProjectUtil.guessProjectForFile(file);
      if (project == null) return null;
    }
    else {
      Logger.getInstance(PsiFileProjectIndependentGist.class).error("Use PsiFileGistImpl with notnull Project");
    }
    return super.getPsiFile(project, file);
  }

  @Override
  protected @Nullable Project getProjectForPersistence(@NotNull PsiFile file) {
    return null;
  }
}
