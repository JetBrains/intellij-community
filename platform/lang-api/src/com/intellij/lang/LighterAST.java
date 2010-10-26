/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Abstract syntax tree built up from light nodes.
 */
public class LighterAST {
  private final CharTable myCharTable;
  private final FlyweightCapableTreeStructure<LighterASTNode> myTreeStructure;

  public LighterAST(final CharTable charTable, final FlyweightCapableTreeStructure<LighterASTNode> treeStructure) {
    myCharTable = charTable;
    myTreeStructure = treeStructure;
  }

  @NotNull
  public CharTable getCharTable() {
    return myCharTable;
  }

  @NotNull
  public LighterASTNode getRoot() {
    return myTreeStructure.getRoot();
  }

  @NotNull
  public List<LighterASTNode> getChildren(@NotNull final LighterASTNode parent) {
    final Ref<LighterASTNode[]> into = new Ref<LighterASTNode[]>();
    final int numKids = myTreeStructure.getChildren(myTreeStructure.prepareForGetChildren(parent), into);
    return numKids > 0 ? CollectionFactory.arrayList(into.get(), 0, numKids) : Collections.<LighterASTNode>emptyList();
  }
}