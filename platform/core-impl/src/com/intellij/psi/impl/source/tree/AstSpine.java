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
package com.intellij.psi.impl.source.tree;

import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.impl.source.StubbedSpine;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class AstSpine implements StubbedSpine {
  static final AstSpine EMPTY_SPINE = new AstSpine(Collections.emptyList()); 
  private final List<CompositeElement> myNodes;

  AstSpine(@NotNull List<CompositeElement> nodes) {
    myNodes = nodes;
  }

  @Override
  public int getStubCount() {
    return myNodes.size();
  }

  @Nullable
  @Override
  public PsiElement getStubPsi(int index) {
    return index >= myNodes.size() ? null : myNodes.get(index).getPsi();
  }

  public int getStubIndex(@NotNull StubBasedPsiElement psi) {
    return myNodes.indexOf((CompositeElement)psi.getNode());
  }

  @Nullable
  @Override
  public IElementType getStubType(int index) {
    return index >= myNodes.size() ? null : myNodes.get(index).getElementType();
  }

  @NotNull
  public List<CompositeElement> getSpineNodes() {
    return myNodes;
  }
}
