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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
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
        return SharedImplUtil.getContainingFile(node);
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

    @Nullable
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
      throw new UnsupportedOperationException();
    }
  }
}
