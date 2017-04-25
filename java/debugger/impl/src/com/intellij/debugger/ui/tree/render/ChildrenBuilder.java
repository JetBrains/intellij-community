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
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.ui.tree.*;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public interface ChildrenBuilder {
  NodeDescriptorFactory  getDescriptorManager();

  NodeManager getNodeManager();
    
  ValueDescriptor getParentDescriptor();

  void setChildren(List<DebuggerTreeNode> children);

  default void addChildren(List<DebuggerTreeNode> children, boolean last) {
    setChildren(children);
  }

  default void setMessage(@NotNull String message,
                  @Nullable Icon icon,
                  @NotNull SimpleTextAttributes attributes,
                  @Nullable XDebuggerTreeNodeHyperlink link) {
  }

  void setRemaining(int remaining);

  void initChildrenArrayRenderer(ArrayRenderer renderer);
}
