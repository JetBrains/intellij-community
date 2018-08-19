// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.actions.AsyncStacksToggleAction;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.debugger.ui.breakpoints.BreakpointIntentionAction;
import com.intellij.debugger.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.debugger.ui.impl.watch.MethodsTracker;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ThreadReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

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
        myTopFrame = createStackFrame(frame);
      }
    }
    catch (EvaluateException e) {
      LOG.info(e);
    }
    finally {
      myTopFrameReady = true;
    }
  }

  @NotNull
  public XStackFrame createStackFrame(@NotNull StackFrameProxyImpl stackFrameProxy) {
    StackFrameDescriptorImpl descriptor = new StackFrameDescriptorImpl(stackFrameProxy, myTracker);

    if (descriptor.getUiIndex() == 1 && myTopFrame instanceof JavaStackFrame) {
      Method method = descriptor.getMethod();
      if (method != null) {
        ((JavaStackFrame)myTopFrame).getDescriptor().putUserData(BreakpointIntentionAction.CALLER_KEY, DebuggerUtilsEx.methodKey(method));
      }
    }

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
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        if (container.isObsolete()) return;
        int status = myThreadProxy.status();
        if (status == ThreadReference.THREAD_STATUS_ZOMBIE) {
          container.errorOccurred(DebuggerBundle.message("frame.panel.thread.finished"));
        }
        else if (!myThreadProxy.isCollected() && myDebugProcess.getSuspendManager().isSuspended(myThreadProxy)) {
          if (!(status == ThreadReference.THREAD_STATUS_UNKNOWN) && !(status == ThreadReference.THREAD_STATUS_NOT_STARTED)) {
            try {
              int added = 0;
              Iterator<StackFrameProxyImpl> iterator = myThreadProxy.frames().iterator();
              if (iterator.hasNext() && firstFrameIndex > 0) {
                iterator.next();
                added++;
              }
              myDebugProcess.getManagerThread().schedule(
                new AppendFrameCommand(suspendContext, iterator, container, added, firstFrameIndex, null));
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
    private final List<StackFrameItem> myAsyncStack;

    public AppendFrameCommand(SuspendContextImpl suspendContext,
                              Iterator<StackFrameProxyImpl> stackFramesIterator,
                              XStackFrameContainer container,
                              int added,
                              int skip,
                              List<StackFrameItem> asyncStack) {
      super(suspendContext);
      myStackFramesIterator = stackFramesIterator;
      myContainer = container;
      myAdded = added;
      mySkip = skip;
      myAsyncStack = asyncStack;
    }

    @Override
    public Priority getPriority() {
      return myAdded <= 10 ? Priority.NORMAL : Priority.LOW;
    }

    private void addFrameIfNeeded(XStackFrame frame, boolean last) {
      if (++myAdded > mySkip) {
        myContainer.addStackFrames(Collections.singletonList(frame), last);
      }
    }

    @Override
    public void contextAction(@NotNull SuspendContextImpl suspendContext) {
      if (myContainer.isObsolete()) return;
      if (myStackFramesIterator.hasNext()) {
        StackFrameProxyImpl frameProxy;
        XStackFrame frame;
        boolean first = myAdded == 0;
        if (first && myTopFrameReady) {
          frame = myTopFrame;
          frameProxy = myStackFramesIterator.next();
        }
        else {
          frameProxy = myStackFramesIterator.next();
          frame = createStackFrame(frameProxy);
          if (first && !myTopFrameReady) {
            myTopFrame = frame;
            myTopFrameReady = true;
          }
        }
        if (first || showFrame(frame)) {
          addFrameIfNeeded(frame, false);
        }

        // replace the rest with the related stack (if available)
        if (myAsyncStack != null) {
          appendRelatedStack(myAsyncStack);
          return;
        }

        List<StackFrameItem> relatedStack = null;
        if (frame instanceof JavaStackFrame &&
            AsyncStacksToggleAction.isAsyncStacksEnabled((XDebugSessionImpl)myDebugProcess.getXdebugProcess().getSession())) {
          relatedStack = StackCapturingLineBreakpoint.getRelatedStack(frameProxy, suspendContext);
          if (relatedStack != null) {
            appendRelatedStack(relatedStack);
            return;
          }
          // append agent stack after the next frame
          relatedStack = AsyncStacksUtils.getAgentRelatedStack((JavaStackFrame)frame, suspendContext);
        }

        myDebugProcess.getManagerThread().schedule(
          new AppendFrameCommand(suspendContext, myStackFramesIterator, myContainer, myAdded, mySkip, relatedStack));
      }
      else {
        myContainer.addStackFrames(Collections.emptyList(), true);
      }
    }

    void appendRelatedStack(@NotNull List<StackFrameItem> asyncStack) {
      int i = 0;
      boolean separator = true;
      for (StackFrameItem stackFrame : asyncStack) {
        if (i > AsyncStacksUtils.getMaxStackLength()) {
          addFrameIfNeeded(new XStackFrame() {
            @Override
            public void customizePresentation(@NotNull ColoredTextContainer component) {
              component.append("Too many frames, the rest is truncated...", SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
            }
          }, true);
          return;
        }
        i++;
        if (stackFrame == null) {
          separator = true;
          continue;
        }
        StackFrameItem.CapturedStackFrame newFrame = stackFrame.createFrame(myDebugProcess);
        if (showFrame(newFrame)) {
          newFrame.setWithSeparator(separator);
          addFrameIfNeeded(newFrame, false);
          separator = false;
        }
      }
      myContainer.addStackFrames(Collections.emptyList(), true);
    }
  }

  private static boolean showFrame(@NotNull XStackFrame frame) {
    if (!XDebuggerSettingsManager.getInstance().getDataViewSettings().isShowLibraryStackFrames() &&
        frame instanceof JVMStackFrameInfoProvider) {
      JVMStackFrameInfoProvider info = (JVMStackFrameInfoProvider)frame;
      return !info.isSynthetic() && !info.isInLibraryContent();
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
