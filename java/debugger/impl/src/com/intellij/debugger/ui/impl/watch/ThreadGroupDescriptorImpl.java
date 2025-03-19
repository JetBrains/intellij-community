// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.tree.ThreadGroupDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.sun.jdi.ObjectCollectedException;

public class ThreadGroupDescriptorImpl extends NodeDescriptorImpl implements ThreadGroupDescriptor {
  private final ThreadGroupReferenceProxyImpl myThreadGroup;
  private boolean myIsCurrent;
  private String myName = null;
  private boolean myIsExpandable = true;

  public ThreadGroupDescriptorImpl(ThreadGroupReferenceProxyImpl threadGroup) {
    myThreadGroup = threadGroup;
  }

  @Override
  public ThreadGroupReferenceProxyImpl getThreadGroupReference() {
    return myThreadGroup;
  }

  public boolean isCurrent() {
    return myIsCurrent;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    ThreadGroupReferenceProxyImpl group = getThreadGroupReference();
    try {
      myName = group.name();
      return JavaDebuggerBundle.message("label.thread.group.node", myName, group.uniqueID());
    }
    catch (ObjectCollectedException e) {
      return myName != null ? JavaDebuggerBundle.message("label.thread.group.node.group.collected", myName) : "";
    }
  }

  @Override
  public boolean isExpandable() {
    return myIsExpandable;
  }

  @Override
  public void setContext(EvaluationContextImpl context) {
    ThreadReferenceProxyImpl threadProxy = context != null ? context.getSuspendContext().getThread() : null;
    myIsCurrent = threadProxy != null && isDescendantGroup(threadProxy.threadGroupProxy());
    myIsExpandable = calcExpandable();
  }

  private boolean isDescendantGroup(ThreadGroupReferenceProxyImpl group) {
    if (group == null) return false;

    if (getThreadGroupReference() == group) return true;

    return isDescendantGroup(group.parent());
  }

  private boolean calcExpandable() {
    ThreadGroupReferenceProxyImpl group = getThreadGroupReference();
    return !group.threads().isEmpty() || !group.threadGroups().isEmpty();
  }
}