// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.action;

import com.intellij.debugger.ui.impl.watch.NodeDescriptorProvider;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DebuggerTreeAction extends XDebuggerTreeActionBase {
  @Nullable
  protected ObjectReference getObjectReference(@NotNull XValueNodeImpl node) {
    XValue valueContainer = node.getValueContainer();
    if (valueContainer instanceof NodeDescriptorProvider) {
      NodeDescriptor descriptor = ((NodeDescriptorProvider) valueContainer).getDescriptor();
      if (descriptor instanceof ValueDescriptorImpl) {
        if (((ValueDescriptorImpl)descriptor).isValueReady()) {
          Value value = ((ValueDescriptorImpl)descriptor).getValue();
          return value instanceof ObjectReference ? (ObjectReference)value : null;
        }
      }
    }

    return null;
  }
}
