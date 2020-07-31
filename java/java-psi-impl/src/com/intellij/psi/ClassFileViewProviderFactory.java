// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.compiled.ClassFileDecompilers;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.compiled.ClassFileDecompilers.Full;

public class ClassFileViewProviderFactory implements FileViewProviderFactory {
  @Override
  public @NotNull FileViewProvider createFileViewProvider(@NotNull VirtualFile file,
                                                          Language language,
                                                          @NotNull PsiManager manager,
                                                          boolean eventSystemEnabled) {
    ClassFileDecompilers.Decompiler decompiler = ClassFileDecompilers.getInstance().find(file);
    if (decompiler instanceof Full) {
      return ((Full)decompiler).createFileViewProvider(file, manager, eventSystemEnabled);
    }

    return new ClassFileViewProvider(manager, file, eventSystemEnabled);
  }
}