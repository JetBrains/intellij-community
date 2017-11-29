/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.util.Getter;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.stubs.StubTree;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.Set;

/**
 * @author peter
 */
final class FileTrees {
  private final Reference<StubTree> myStub;
  private final Getter<FileElement> myTreeElementPointer; // SoftReference/WeakReference to ASTNode or a strong reference to a tree if the file is a DummyHolder
  final boolean astLoaded;
  final boolean useStrongRefs;

  private FileTrees(@Nullable Reference<StubTree> stub, @Nullable Getter<FileElement> ast, boolean astLoaded, boolean useStrongRefs) {
    this.myStub = stub;
    this.myTreeElementPointer = ast;
    this.astLoaded = astLoaded;
    this.useStrongRefs = useStrongRefs;
  }

  @Nullable
  StubTree derefStub() {
    return SoftReference.dereference(myStub);
  }

  @Nullable
  FileElement derefTreeElement() {
    return SoftReference.deref(myTreeElementPointer);
  }

  FileTrees switchToStrongRefs() {
    return new FileTrees(myStub, myTreeElementPointer, astLoaded, true);
  }

  FileTrees clearStub(@NotNull String reason) {
    StubTree stubHolder = derefStub();
    if (stubHolder != null) {
      ((PsiFileStubImpl<?>)stubHolder.getRoot()).clearPsi(reason);
    }
    return new FileTrees(null, myTreeElementPointer, astLoaded, useStrongRefs);
  }

  FileTrees withAst(@NotNull Getter<FileElement> ast) {
    return new FileTrees(myStub, ast, true, useStrongRefs);
  }

  FileTrees withExclusiveStub(@NotNull StubTree stub, Set<PsiFileImpl> allRoots) {
    if (derefTreeElement() != null || useStrongRefs) {
      throw new RuntimeException(toString() + "; roots=" + allRoots + "; root trees=" + ContainerUtil.map(allRoots, PsiFileImpl::getFileTrees));
    }
    return new FileTrees(new SoftReference<>(stub), null, false, false);
  }

  FileTrees withGreenStub(@NotNull StubTree stub, @NotNull PsiFileImpl file) {
    if (derefTreeElement() == null || !astLoaded) {
      throw new RuntimeException("No AST in file " + file + " of " + file.getClass() + "; " + this);
    }
    return new FileTrees(new SoftReference<>(stub), myTreeElementPointer, true, useStrongRefs);
  }

  static FileTrees noStub(@Nullable FileElement ast, @NotNull PsiFileImpl file) {
    return new FileTrees(null, ast, ast != null, file instanceof DummyHolder);
  }

  @Override
  public String toString() {
    return "FileTrees{" +
           "stub=" + (myStub == null ? "noRef" : derefStub()) +
           ", AST=" + (myTreeElementPointer == null ? "noRef" : derefTreeElement()) +
           ", astLoaded=" + astLoaded +
           ", useStrongRefs=" + useStrongRefs +
           '}' ;
  }
}
