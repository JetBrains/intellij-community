// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeEditor;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.indexing.BinaryFileSourceProvider;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaBinaryFileSourceProvider implements BinaryFileSourceProvider {

  @Override
  public @Nullable PsiFile findSourceFile(@NotNull PsiBinaryFile file) {
    VirtualFile virtualFile = JavaEditorFileSwapper.findSourceFile(file.getProject(), file.getVirtualFile());
    PsiManager manager = file.getManager();
    if (virtualFile == null) return null;
    return manager.findFile(virtualFile);
  }
}