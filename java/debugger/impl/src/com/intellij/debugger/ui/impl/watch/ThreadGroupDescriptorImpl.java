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
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.tree.ThreadGroupDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
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
    ThreadReferenceProxyImpl threadProxy = context != null? context.getSuspendContext().getThread() : null;
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