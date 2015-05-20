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

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class StubTree extends ObjectStubTree<StubElement<?>> {

  public StubTree(@NotNull final PsiFileStub root) {
    this(root, true);
  }

  public StubTree(@NotNull final PsiFileStub root, final boolean withBackReference) {
    super((ObjectStubBase)root, withBackReference);
  }

  @Override
  protected void enumerateStubs(@NotNull Stub root, @NotNull List<Stub> result) {
    final PsiFileStub[] files = ((PsiFileStub)root).getStubRoots();
    int idOffset = 0;
    final List<Stub> dummyList = new ArrayList<Stub>();
    for (PsiFileStub file : files) {
      if (file == root) break;
      final int fileStubsCount;
      final ObjectStubTree existingTree = file.getUserData(STUB_TO_TREE_REFERENCE);
      if (existingTree != null) {
        fileStubsCount = existingTree.getPlainList().size();
      }
      else {
        dummyList.clear();
        enumerateStubs(file, dummyList, idOffset);
        fileStubsCount = dummyList.size();
      }
      idOffset += fileStubsCount;
    }
    enumerateStubs(root, result, idOffset);
  }

  @NotNull
  @Override
  public List<StubElement<?>> getPlainListFromAllRoots() {
    final PsiFileStub[] roots = getRoot().getStubRoots();
    if (roots.length == 1) return super.getPlainListFromAllRoots();

    return ContainerUtil.concat(roots, new Function<PsiFileStub, Collection<? extends StubElement<?>>>() {
      @Override
      public Collection<? extends StubElement<?>> fun(PsiFileStub stub) {
        final ObjectStubTree existingTree = stub.getUserData(STUB_TO_TREE_REFERENCE);
        //noinspection unchecked
        return existingTree != null ? existingTree.getPlainList() : new StubTree(stub, false).getPlainList();
      }
    });
  }

  @NotNull
  @Override
  public PsiFileStub getRoot() {
    return (PsiFileStub)myRoot;
  }
}
