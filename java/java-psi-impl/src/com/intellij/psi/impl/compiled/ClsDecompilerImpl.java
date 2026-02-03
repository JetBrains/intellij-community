// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ClassFileViewProvider;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiManager;
import com.intellij.psi.compiled.ClassFileDecompilers;
import com.intellij.psi.compiled.ClsStubBuilder;
import com.intellij.psi.impl.source.JavaFileElementType;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ClsDecompilerImpl extends ClassFileDecompilers.Full {
  private static final Logger LOG = Logger.getInstance(ClsDecompilerImpl.class);
  private final ClsStubBuilder myStubBuilder = new MyClsStubBuilder();

  @Override
  public boolean accepts(@NotNull VirtualFile file) {
    return true;
  }

  @Override
  public @NotNull ClsStubBuilder getStubBuilder() {
    return myStubBuilder;
  }

  @Override
  public @NotNull FileViewProvider createFileViewProvider(@NotNull VirtualFile file, @NotNull PsiManager manager, boolean physical) {
    return new ClassFileViewProvider(manager, file, physical);
  }

  private static class MyClsStubBuilder extends ClsStubBuilder {
    @Override
    public int getStubVersion() {
      return JavaFileElementType.STUB_VERSION;
    }

    @Override
    public @Nullable PsiFileStub<?> buildFileStub(@NotNull FileContent fileContent) throws ClsFormatException {
      PsiFileStub<?> stub = ClsFileImpl.buildFileStub(fileContent.getFile(), fileContent.getContent());
      if (stub == null && fileContent.getFileName().indexOf('$') < 0) {
        LOG.info("No stub built for the file " + fileContent);
      }
      return stub;
    }
  }
}
