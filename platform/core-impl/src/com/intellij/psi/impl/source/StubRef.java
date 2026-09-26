// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;

/**
 * A substrate backed by a direct, strong reference to a {@link StubElement}.
 * This is the initial substrate for PSI elements created from stubs (e.g. during stub-based
 * resolve or indexing). It does not support {@link #getNode()} — calling it throws
 * {@link UnsupportedOperationException} because AST is not loaded in this state.
 */
final class StubRef extends SubstrateRef {
  private final StubElement<?> myStub;

  StubRef(@NotNull StubElement<?> stub) {
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
      new PsiInvalidElementAccessException(myStub.getPsi(),
                                           "no psi for file stub " + stub + " (" + stub.getClass() + "), invalidation reason=" + reason,
                                           null);
    if (PsiFileImpl.STUB_PSI_MISMATCH.equals(reason)) {
      // we're between finding stub-psi mismatch and the next EDT spot where the file is reparsed and stub rebuilt
      //    see com.intellij.psi.impl.source.PsiFileImpl.rebuildStub()
      // most likely it's just another highlighting thread accessing the same PSI concurrently and not yet canceled, so cancel it
      throw new ProcessCanceledException(exception);
    }
    throw exception;
  }
}
