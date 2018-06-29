/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class SubstrateRef {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.SubstrateRef");

  @NotNull
  public abstract ASTNode getNode();

  @Nullable
  public Stub getStub() {
    return null;
  }

  @Nullable
  public Stub getGreenStub() {
    return getStub();
  }

  public abstract boolean isValid();

  @NotNull
  public abstract PsiFile getContainingFile();

  @NotNull
  static SubstrateRef createInvalidRef(@NotNull final StubBasedPsiElementBase<?> psi) {
    return new SubstrateRef() {
      @NotNull
      @Override
      public ASTNode getNode() {
        throw new PsiInvalidElementAccessException(psi);
      }

      @Override
      public boolean isValid() {
        return false;
      }

      @NotNull
      @Override
      public PsiFile getContainingFile() {
        throw new PsiInvalidElementAccessException(psi);
      }
    };
  }

  public static SubstrateRef createAstStrongRef(@NotNull final ASTNode node) {
    return new SubstrateRef() {

      @NotNull
      @Override
      public ASTNode getNode() {
        return node;
      }

      @Override
      public boolean isValid() {
        FileASTNode fileElement = SharedImplUtil.findFileElement(node);
        PsiElement file = fileElement == null ? null : fileElement.getPsi();
        return file != null && file.isValid();
      }

      @NotNull
      @Override
      public PsiFile getContainingFile() {
        PsiFile file = SharedImplUtil.getContainingFile(node);
        if (file == null) throw PsiInvalidElementAccessException.createByNode(node, null);
        return file;
      }
    };
  }

  public static class StubRef extends SubstrateRef {
    private final StubElement myStub;

    public StubRef(@NotNull StubElement stub) {
      myStub = stub;
    }

    @NotNull
    @Override
    public ASTNode getNode() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Stub getStub() {
      return myStub;
    }

    @Override
    public boolean isValid() {
      StubElement parent = myStub.getParentStub();
      if (parent == null) {
        LOG.error("No parent for stub " + myStub + " of class " + myStub.getClass());
        return false;
      }
      PsiElement psi = parent.getPsi();
      return psi != null && psi.isValid();
    }

    @NotNull
    @Override
    public PsiFile getContainingFile() {
      StubElement stub = myStub;
      while (!(stub instanceof PsiFileStub)) {
        stub = stub.getParentStub();
      }
      PsiFile psi = (PsiFile)stub.getPsi();
      if (psi != null) {
        return psi;
      }
      return reportError(stub);
    }

    private PsiFile reportError(StubElement stub) {
      ApplicationManager.getApplication().assertReadAccessAllowed();

      String reason = ((PsiFileStubImpl<?>)stub).getInvalidationReason();
      PsiInvalidElementAccessException exception =
        new PsiInvalidElementAccessException(myStub.getPsi(), "no psi for file stub " + stub + ", invalidation reason=" + reason, null);
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
