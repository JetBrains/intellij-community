// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.BinaryFileDecompiler;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.compiled.ClassFileDecompilers;
import org.jetbrains.annotations.NotNull;

public final class ClassFileDecompiler implements BinaryFileDecompiler {
  private static final Logger LOG = Logger.getInstance(ClassFileDecompiler.class);

  @Override
  public @NotNull CharSequence decompile(@NotNull VirtualFile file) {
    ClassFileDecompilers.Decompiler decompiler = ClassFileDecompilers.getInstance().find(file, ClassFileDecompilers.Decompiler.class);
    if (decompiler instanceof ClsDecompilerImpl) {
      return ClsFileImpl.decompile(file);
    }

    if (decompiler instanceof ClassFileDecompilers.Full) {
      PsiManager manager = PsiManager.getInstance(DefaultProjectFactory.getInstance().getDefaultProject());
      return ((ClassFileDecompilers.Full)decompiler).createFileViewProvider(file, manager, true).getContents();
    }

    if (decompiler instanceof ClassFileDecompilers.Light) {
      try {
        return ((ClassFileDecompilers.Light)decompiler).getText(file);
      }
      catch (ClassFileDecompilers.Light.CannotDecompileException e) {
        LOG.warn("decompiler: " + decompiler.getClass(), e);
        return ClsFileImpl.decompile(file);
      }
    }

    throw new IllegalStateException(decompiler.getClass().getName() +
                                    " should be on of " +
                                    ClassFileDecompilers.Full.class.getName() +
                                    " or " +
                                    ClassFileDecompilers.Light.class.getName());
  }
}