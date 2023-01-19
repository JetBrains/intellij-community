/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.impl.source.StubbedSpine;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class StubTree extends ObjectStubTree<StubElement<?>> {
  private final StubSpine mySpine = new StubSpine(this);

  public StubTree(@NotNull PsiFileStub root) {
    this(root, true);
  }

  public StubTree(@NotNull PsiFileStub root, boolean withBackReference) {
    super((ObjectStubBase)root, withBackReference);
  }

  @NotNull
  @Override
  protected List<StubElement<?>> enumerateStubs(@NotNull Stub root) {
    return ((StubBase<?>)root).myStubList.finalizeLoadingStage().toPlainList();
  }

  @NotNull
  @Override
  final List<StubElement<?>> getPlainListFromAllRoots() {
    PsiFileStub[] roots = ((PsiFileStubImpl<?>)getRoot()).getStubRoots();
    if (roots.length == 1) return super.getPlainListFromAllRoots();

    return ContainerUtil.concat(roots, stub -> ((PsiFileStubImpl<?>)stub).myStubList.toPlainList());
  }

  @NotNull
  @Override
  public PsiFileStub getRoot() {
    return (PsiFileStub)myRoot;
  }

  @NotNull
  public StubbedSpine getSpine() {
    return mySpine;
  }
}
