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
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.StubbedSpine;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class StubSpine implements StubbedSpine {
  private final StubTree myTree;

  StubSpine(@NotNull StubTree tree) {
    myTree = tree;
  }

  @Override
  public int getStubCount() {
    return myTree.getPlainList().size();
  }

  @Nullable
  @Override
  public PsiElement getStubPsi(int index) {
    List<StubElement<?>> stubs = myTree.getPlainList();
    return index >= stubs.size() ? null : stubs.get(index).getPsi();
  }

  @Nullable
  @Override
  public IElementType getStubType(int index) {
    List<StubElement<?>> stubs = myTree.getPlainList();
    return index >= stubs.size() ? null : stubs.get(index).getStubType();
  }
}
