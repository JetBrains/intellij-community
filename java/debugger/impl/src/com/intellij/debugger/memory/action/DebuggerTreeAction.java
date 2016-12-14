package org.jetbrains.debugger.memory.action;

import com.intellij.debugger.ui.impl.watch.NodeDescriptorProvider;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
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
      if (descriptor instanceof ValueDescriptor) {
        Value value = ((ValueDescriptor)descriptor).getValue();
        return value instanceof ObjectReference ? (ObjectReference) value : null;
      }
    }

    return null;
  }
}
