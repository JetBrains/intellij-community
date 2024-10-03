// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public abstract class SubstrateRef {
  private static final Logger LOG = Logger.getInstance(SubstrateRef.class);

  public abstract @NotNull ASTNode getNode();

  public @Nullable Stub getStub() {
    return null;
  }

  public @Nullable Stub getGreenStub() {
    return getStub();
  }

  public abstract boolean isValid();

  public abstract @NotNull PsiFile getContainingFile();

  static @NotNull SubstrateRef createInvalidRef(@NotNull StubBasedPsiElementBase<?> psi) {
    return new SubstrateRef() {
      @Override
      public @NotNull ASTNode getNode() {
        throw new PsiInvalidElementAccessException(psi);
      }

      @Override
      public boolean isValid() {
        return false;
      }

      @Override
      public @NotNull PsiFile getContainingFile() {
        throw new PsiInvalidElementAccessException(psi);
      }
    };
  }

  public static @NotNull SubstrateRef createAstStrongRef(@NotNull ASTNode node) {
    return new SubstrateRef() {

      @Override
      public @NotNull ASTNode getNode() {
        return node;
      }

      @Override
      public boolean isValid() {
        FileASTNode fileElement = SharedImplUtil.findFileElement(node);
        PsiElement file = fileElement == null ? null : fileElement.getPsi();
        return file != null && file.isValid();
      }

      @Override
      public @NotNull PsiFile getContainingFile() {
        PsiFile file = SharedImplUtil.getContainingFile(node);
        if (file == null) throw PsiInvalidElementAccessException.createByNode(node, null);
        return file;
      }
    };
  }

  public static class StubRef extends SubstrateRef {
    private final StubElement<?> myStub;

    public StubRef(@NotNull StubElement<?> stub) {
      myStub = stub;
    }

    @Override
    public @NotNull ASTNode getNode() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull Stub getStub() {
      return myStub;
    }

    @Override
    public boolean isValid() {
      PsiFileStub<?> fileStub = myStub.getContainingFileStub();
      if (fileStub == null) return false;
      PsiFile psi = fileStub.getPsi();
      return psi != null && psi.isValid();
    }

    @Override
    public @NotNull PsiFile getContainingFile() {
      PsiFileStub<?> stub = myStub.getContainingFileStub();
      if (stub == null) {
        throw new PsiInvalidElementAccessException(myStub.getPsi(),
                                                   "stub hierarchy is invalid: " + this + " (" + getClass() + ")" +
                                                   " has null containing file stub", null);
      }
      PsiFile psi = stub.getPsi();
      if (psi != null) {
        return psi;
      }
      return reportFileInvalidError(stub);
    }

    private PsiFile reportFileInvalidError(@NotNull PsiFileStub<?> stub) {
      ApplicationManager.getApplication().assertReadAccessAllowed();

      String reason = stub.getInvalidationReason();
      PsiInvalidElementAccessException exception =
        new PsiInvalidElementAccessException(myStub.getPsi(), "no psi for file stub " + stub + " ("+stub.getClass()+"), invalidation reason=" + reason, null);
      if (PsiFileImpl.STUB_PSI_MISMATCH.equals(reason)) {
        // we're between finding stub-psi mismatch and the next EDT spot where the file is reparsed and stub rebuilt
        //    see com.intellij.psi.impl.source.PsiFileImpl.rebuildStub()
        // most likely it's just another highlighting thread accessing the same PSI concurrently and not yet canceled, so cancel it
        throw new ProcessCanceledException(exception);
      }
      throw exception;
    }
  }
}
