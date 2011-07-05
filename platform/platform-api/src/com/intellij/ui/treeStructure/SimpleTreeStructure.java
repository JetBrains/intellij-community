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
package com.intellij.ui.treeStructure;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import org.jetbrains.annotations.NotNull;

public abstract class SimpleTreeStructure extends AbstractTreeStructure {

  public Object[] getChildElements(Object element) {
    return ((SimpleNode) element).getChildren();
  }

  public Object getParentElement(Object element) {
    return ((SimpleNode) element).getParent();
  }

  @Override
  public boolean isAlwaysLeaf(Object element) {
    return ((SimpleNode)element).isAlwaysLeaf();
  }

  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    return (NodeDescriptor) element;
  }

  public void commit() {
  }

  public boolean hasSomethingToCommit() {
    return false;
  }

  public final void clearCaches() {
    cleanUpCaches((SimpleNode) getRootElement());
  }

  private void cleanUpCaches(SimpleNode node) {
    if (!(node instanceof CachingSimpleNode)) return;

    final CachingSimpleNode cachingNode = ((CachingSimpleNode) node);
    if (cachingNode.getCached() == null) return;

    for (SimpleNode eachChild : cachingNode.myChildren) {
      cleanUpCaches(eachChild);
    }

    cachingNode.cleanUpCache();
  }

  public static class Impl extends SimpleTreeStructure {
    private final SimpleNode myRoot;

    public Impl(SimpleNode root) {
      myRoot = root;
    }

    public Object getRootElement() {
      return myRoot;
    }
  }

}
