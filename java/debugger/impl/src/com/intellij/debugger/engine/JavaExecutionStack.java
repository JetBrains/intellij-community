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
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.impl.watch.MethodsTracker;
import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.frame.XExecutionStack;
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
  private final ThreadReferenceProxyImpl myThreadProxy;
  private final DebugProcessImpl myDebugProcess;
  private volatile JavaStackFrame myTopFrame;
  private volatile boolean myTopFrameReady = false;
  private final MethodsTracker myTracker = new MethodsTracker();

  public JavaExecutionStack(@NotNull ThreadReferenceProxyImpl threadProxy, @NotNull DebugProcessImpl debugProcess, boolean current) {
    super(calcRepresentation(threadProxy), calcIcon(threadProxy, current));
    myThreadProxy = threadProxy;
    myDebugProcess = debugProcess;
    if (current) {
      initTopFrame();
    }
  }

  private static Icon calcIcon(ThreadReferenceProxyImpl threadProxy, boolean current) {
    if (current) {
      return AllIcons.Debugger.ThreadCurrent;
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
        myTopFrame = new JavaStackFrame(frame, myTracker);
      }
    }
    catch (EvaluateException e) {
      e.printStackTrace();
    }
    finally {
      myTopFrameReady = true;
    }
  }

  @Nullable
  @Override
  public JavaStackFrame getTopFrame() {
    assert myTopFrameReady : "Top frame must be already calculated here";
    return myTopFrame;
  }

  @Override
  public void computeStackFrames(final int firstFrameIndex, final XStackFrameContainer container) {
    myDebugProcess.getManagerThread().schedule(new DebuggerContextCommandImpl(myDebugProcess.getDebuggerContext()) {
      @Override
      public Priority getPriority() {
        return Priority.NORMAL;
      }

      @Override
      public void threadAction() {
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
      if (myStackFramesIterator.hasNext()) {
        JavaStackFrame frame;
        boolean first = myAdded == 0;
        if (first && myTopFrameReady) {
          frame = myTopFrame;
          myStackFramesIterator.next();
        }
        else {
          frame = new JavaStackFrame(myStackFramesIterator.next(), myTracker);
          if (first && !myTopFrameReady) {
            myTopFrame = frame;
            myTopFrameReady = true;
          }
        }
        if (first || DebuggerSettings.getInstance().SHOW_LIBRARY_STACKFRAMES || (!frame.getDescriptor().isSynthetic() && !frame.getDescriptor().isInLibraryContent())) {
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
}
