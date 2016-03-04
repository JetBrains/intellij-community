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
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.impl.descriptors.data.DescriptorKey;
import com.intellij.debugger.ui.tree.NodeDescriptor;

import java.util.HashMap;
import java.util.Map;

public class MarkedDescriptorTree {
  private final HashMap<NodeDescriptor, Map<DescriptorKey<? extends NodeDescriptor>, NodeDescriptor>> myChildrenMap = new HashMap<>();
  private final Map<DescriptorKey<? extends NodeDescriptor>, NodeDescriptor> myRootChildren = new com.intellij.util.containers.HashMap<>();

  public <T extends NodeDescriptor> void addChild(NodeDescriptor parent, T child, DescriptorKey<T> key) {
    Map<DescriptorKey<? extends NodeDescriptor>, NodeDescriptor> children;

    if(parent == null) {
      children = myRootChildren;
    }
    else {
      children = myChildrenMap.get(parent);
      if(children == null) {
        children = new com.intellij.util.containers.HashMap<>();
        myChildrenMap.put(parent, children);
      }
    }
    children.put(key, child);
  }

  public <T extends NodeDescriptor> T getChild(NodeDescriptor parent, DescriptorKey<T> key) {
    if(parent == null) {
      return (T)myRootChildren.get(key);
    }
    final Map<DescriptorKey<? extends NodeDescriptor>, NodeDescriptor> map = myChildrenMap.get(parent);
    return (T)(map != null ? map.get(key) : null);
  }

  public void clear() {
    myChildrenMap.clear();
    myRootChildren.clear();
  }
}
