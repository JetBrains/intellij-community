/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang;

import com.intellij.openapi.util.Ref;
import com.intellij.util.CharTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class FCTSBackedLighterAST extends LighterAST {
  private final FlyweightCapableTreeStructure<LighterASTNode> myTreeStructure;

  public FCTSBackedLighterAST(final CharTable charTable, final FlyweightCapableTreeStructure<LighterASTNode> treeStructure) {
    super(charTable);
    myTreeStructure = treeStructure;
  }

  @NotNull
  @Override
  public LighterASTNode getRoot() {
    return myTreeStructure.getRoot();
  }

  @Override
  public LighterASTNode getParent(@NotNull final LighterASTNode node) {
    return myTreeStructure.getParent(node);
  }

  @NotNull
  @Override
  public List<LighterASTNode> getChildren(@NotNull final LighterASTNode parent) {
    final Ref<LighterASTNode[]> into = new Ref<LighterASTNode[]>();
    final int numKids = myTreeStructure.getChildren(myTreeStructure.prepareForGetChildren(parent), into);
    return numKids > 0 ? ContainerUtil.newArrayList(into.get(), 0, numKids) : ContainerUtil.<LighterASTNode>emptyList();
  }
}
