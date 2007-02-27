package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.tree.ThreadGroupDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.debugger.DebuggerBundle;
import com.sun.jdi.ObjectCollectedException;

public class ThreadGroupDescriptorImpl extends NodeDescriptorImpl implements ThreadGroupDescriptor{
  private final ThreadGroupReferenceProxyImpl myThreadGroup;
  private boolean myIsCurrent;
  private String myName = null;
  private boolean myIsExpandable = true;

  public ThreadGroupDescriptorImpl(ThreadGroupReferenceProxyImpl threadGroup) {
    myThreadGroup = threadGroup;
  }

  public ThreadGroupReferenceProxyImpl getThreadGroupReference() {
    return myThreadGroup;
  }

  public boolean isCurrent() {
    return myIsCurrent;
  }

  public String getName() {
    return myName;
  }

  protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    ThreadGroupReferenceProxyImpl group = getThreadGroupReference();
    try {
      myName = group.name();
      return DebuggerBundle.message("label.thread.group.node", myName, group.uniqueID());
    }
    catch (ObjectCollectedException e) {
      return myName != null ? DebuggerBundle.message("label.thread.group.node.group.collected", myName) : "";
    }
  }

  public boolean isExpandable() {
    return myIsExpandable;
  }

  public void setContext(EvaluationContextImpl context) {
    ThreadReferenceProxyImpl threadProxy = context.getSuspendContext().getThread();
    myIsCurrent = threadProxy != null && isDescendantGroup(threadProxy.threadGroupProxy());
    myIsExpandable = calcExpandable();
  }

  private boolean isDescendantGroup(ThreadGroupReferenceProxyImpl group) {
    if(group == null) return false;

    if(getThreadGroupReference() == group) return true;

    return isDescendantGroup(group.parent());
  }

  private boolean calcExpandable() {
    ThreadGroupReferenceProxyImpl group = getThreadGroupReference();
    return group.threads().size() > 0 || group.threadGroups().size() > 0;
  }
}