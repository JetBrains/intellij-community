package org.jetbrains.debugger.memory.action;

import com.intellij.debugger.ui.impl.watch.NodeDescriptorProvider;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.memory.view.InstancesWindow;

public class ShowInstancesByClassAction extends XDebuggerTreeActionBase {
  @Override
  protected boolean isEnabled(@NotNull XValueNodeImpl node, @NotNull AnActionEvent e) {
    ObjectReference ref = getObjectReference(node);
    if(ref != null) {
      String text = String.format("Show %s Objects...", StringUtil.getShortName(ref.referenceType().name()));
      e.getPresentation().setText(text);
    }

    return ref != null;
  }

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      XDebugSession debugSession = XDebuggerManager.getInstance(project).getCurrentSession();
      ObjectReference ref = getObjectReference(node);
      if(debugSession != null && ref != null){
        ReferenceType referenceType = ref.referenceType();
        new InstancesWindow(debugSession, referenceType::instances, referenceType.name()).show();
      }
    }
  }

  @Nullable
  private ObjectReference getObjectReference(@NotNull XValueNodeImpl node) {
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
