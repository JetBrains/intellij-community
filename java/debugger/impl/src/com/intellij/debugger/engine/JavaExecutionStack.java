/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.impl.watch.MethodsTracker;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import com.sun.jdi.Location;
import com.sun.jdi.ThreadReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Iterator;

/**
 * @author egor
 */
public class JavaExecutionStack extends XExecutionStack {
  private static final Logger LOG = Logger.getInstance(JavaExecutionStack.class);

  private final ThreadReferenceProxyImpl myThreadProxy;
  private final DebugProcessImpl myDebugProcess;
  private volatile XStackFrame myTopFrame;
  private volatile boolean myTopFrameReady = false;
  private final MethodsTracker myTracker = new MethodsTracker();

  public JavaExecutionStack(@NotNull ThreadReferenceProxyImpl threadProxy, @NotNull DebugProcessImpl debugProcess, boolean current) {
    super(calcRepresentation(threadProxy), calcIcon(threadProxy, current));
    myThreadProxy = threadProxy;
    myDebugProcess = debugProcess;
  }

  private static Icon calcIcon(ThreadReferenceProxyImpl threadProxy, boolean current) {
    if (current) {
      return threadProxy.isSuspended() ? AllIcons.Debugger.ThreadCurrent : AllIcons.Debugger.ThreadRunning;
    }
    else if (threadProxy.isAtBreakpoint()) {
      return AllIcons.Debugger.ThreadAtBreakpoint;
    }
    else if (threadProxy.isSuspended()) {
      return AllIcons.Debugger.ThreadSuspended;
    }
    else {
      return AllIcons.Debugger.ThreadRunning;
    }
  }

  @NotNull
  ThreadReferenceProxyImpl getThreadProxy() {
    return myThreadProxy;
  }

  public final void initTopFrame() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      StackFrameProxyImpl frame = myThreadProxy.frame(0);
      if (frame != null) {
        myTopFrame = createStackFrame(frame, myTracker);
      }
    }
    catch (EvaluateException e) {
      LOG.info(e);
    }
    finally {
      myTopFrameReady = true;
    }
  }

  private static XStackFrame createStackFrame(@NotNull StackFrameProxyImpl stackFrameProxy, @NotNull MethodsTracker tracker) {
    StackFrameDescriptorImpl descriptor = new StackFrameDescriptorImpl(stackFrameProxy, tracker);
    DebugProcessImpl debugProcess = (DebugProcessImpl)descriptor.getDebugProcess();
    Location location = descriptor.getLocation();
    if (location != null) {
      XStackFrame customFrame = debugProcess.getPositionManager().createStackFrame(stackFrameProxy, debugProcess, location);
      if (customFrame != null) {
        return customFrame;
      }
    }
    return new JavaStackFrame(descriptor, true);
  }

  @Nullable
  @Override
  public XStackFrame getTopFrame() {
    assert myTopFrameReady : "Top frame must be already calculated here";
    return myTopFrame;
  }

  @Override
  public void computeStackFrames(final int firstFrameIndex, final XStackFrameContainer container) {
    if (container.isObsolete()) return;
    myDebugProcess.getManagerThread().schedule(new SuspendContextCommandImpl(myDebugProcess.getDebuggerContext().getSuspendContext()) {
      @Override
      public Priority getPriority() {
        return Priority.NORMAL;
      }

      @Override
      public void contextAction() throws Exception {
        if (container.isObsolete()) return;
        if (!myThreadProxy.isCollected() && myDebugProcess.getSuspendManager().isSuspended(myThreadProxy)) {
          int status = myThreadProxy.status();
          if (!(status == ThreadReference.THREAD_STATUS_UNKNOWN) &&
              !(status == ThreadReference.THREAD_STATUS_NOT_STARTED) &&
              !(status == ThreadReference.THREAD_STATUS_ZOMBIE)) {
            try {
              int added = 0;
              Iterator<StackFrameProxyImpl> iterator = myThreadProxy.frames().iterator();
              if (iterator.hasNext() && firstFrameIndex > 0) {
                iterator.next();
                added++;
              }
              myDebugProcess.getManagerThread().schedule(new AppendFrameCommand(getSuspendContext(), iterator, container, added, firstFrameIndex));
            }
            catch (EvaluateException e) {
              container.errorOccurred(e.getMessage());
            }
          }
        }
        else {
          container.errorOccurred(DebuggerBundle.message("frame.panel.frames.not.available"));
        }
      }
    });
  }

  private class AppendFrameCommand extends SuspendContextCommandImpl {
    private final Iterator<StackFrameProxyImpl> myStackFramesIterator;
    private final XStackFrameContainer myContainer;
    private int myAdded;
    private final int mySkip;

    public AppendFrameCommand(SuspendContextImpl suspendContext,
                              Iterator<StackFrameProxyImpl> stackFramesIterator,
                              XStackFrameContainer container,
                              int added,
                              int skip) {
      super(suspendContext);
      myStackFramesIterator = stackFramesIterator;
      myContainer = container;
      myAdded = added;
      mySkip = skip;
    }

    @Override
    public Priority getPriority() {
      return myAdded <= 10 ? Priority.NORMAL : Priority.LOW;
    }

    @Override
    public void contextAction() throws Exception {
      if (myContainer.isObsolete()) return;
      if (myStackFramesIterator.hasNext()) {
        XStackFrame frame;
        boolean first = myAdded == 0;
        if (first && myTopFrameReady) {
          frame = myTopFrame;
          myStackFramesIterator.next();
        }
        else {
          frame = createStackFrame(myStackFramesIterator.next(), myTracker);
          if (first && !myTopFrameReady) {
            myTopFrame = frame;
            myTopFrameReady = true;
          }
        }
        if (first || showFrame(frame)) {
          if (++myAdded > mySkip) {
            myContainer.addStackFrames(Collections.singletonList(frame), false);
          }
        }
        myDebugProcess.getManagerThread().schedule(
          new AppendFrameCommand(getSuspendContext(), myStackFramesIterator, myContainer, myAdded, mySkip));
      }
      else {
        myContainer.addStackFrames(Collections.<JavaStackFrame>emptyList(), true);
      }
    }
  }

  private static boolean showFrame(@NotNull XStackFrame frame) {
    if (XDebuggerSettingsManager.getInstance().getDataViewSettings().isShowLibraryStackFrames()) return true;
    if (frame instanceof JavaStackFrame) {
      StackFrameDescriptorImpl descriptor = ((JavaStackFrame)frame).getDescriptor();
      return !descriptor.isSynthetic() && !descriptor.isInLibraryContent();
    }
    return true;
  }

  private static String calcRepresentation(ThreadReferenceProxyImpl thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    String name = thread.name();
    ThreadGroupReferenceProxyImpl gr = thread.threadGroupProxy();
    final String grname = (gr != null)? gr.name() : null;
    final String threadStatusText = DebuggerUtilsEx.getThreadStatusText(thread.status());
    //noinspection HardCodedStringLiteral
    if (grname != null && !"SYSTEM".equalsIgnoreCase(grname)) {
      return DebuggerBundle.message("label.thread.node.in.group", name, thread.uniqueID(), threadStatusText, grname);
    }
    return DebuggerBundle.message("label.thread.node", name, thread.uniqueID(), threadStatusText);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    JavaExecutionStack stack = (JavaExecutionStack)o;

    if (!myThreadProxy.equals(stack.myThreadProxy)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myThreadProxy.hashCode();
  }

  @Override
  public String toString() {
    return getDisplayName();
  }
}
