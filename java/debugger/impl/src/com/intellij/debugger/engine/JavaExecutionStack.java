// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.actions.AsyncStacksToggleAction;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.debugger.ui.breakpoints.BreakpointIntentionAction;
import com.intellij.debugger.ui.impl.watch.MethodsTracker;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import com.jetbrains.jdi.ThreadGroupReferenceImpl;
import com.jetbrains.jdi.ThreadReferenceImpl;
import com.sun.jdi.Method;
import com.sun.jdi.ThreadReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class JavaExecutionStack extends XExecutionStack {
  private static final Logger LOG = Logger.getInstance(JavaExecutionStack.class);

  private final ThreadReferenceProxyImpl myThreadProxy;
  private final DebugProcessImpl myDebugProcess;
  private volatile List<XStackFrame> myTopFrames;
  private volatile boolean myTopFramesReady = false;
  private final MethodsTracker myTracker = new MethodsTracker();

  public JavaExecutionStack(@NotNull ThreadReferenceProxyImpl threadProxy, @NotNull DebugProcessImpl debugProcess, boolean current) {
    super(calcRepresentation(threadProxy), calcIcon(threadProxy, current));
    myThreadProxy = threadProxy;
    myDebugProcess = debugProcess;
  }

  private JavaExecutionStack(@NlsContexts.ListItem @NotNull String displayName,
                             @Nullable Icon icon,
                             @NotNull ThreadReferenceProxyImpl threadProxy,
                             @NotNull DebugProcessImpl debugProcess) {
    super(displayName, icon);
    myThreadProxy = threadProxy;
    myDebugProcess = debugProcess;
  }

  public static CompletableFuture<JavaExecutionStack> create(@NotNull ThreadReferenceProxyImpl threadProxy,
                                                             @NotNull DebugProcessImpl debugProcess,
                                                             boolean current) {
    return calcRepresentationAsync(threadProxy)
      .thenCombine(calcIconAsync(threadProxy, current),
                   (@NlsContexts.ListItem var text, var icon) -> {
                     return new JavaExecutionStack(text, icon, threadProxy, debugProcess);
                   });
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

  private static CompletableFuture<Icon> calcIconAsync(ThreadReferenceProxyImpl threadProxy, boolean current) {
    ThreadReference ref = threadProxy.getThreadReference();
    if (!DebuggerUtilsAsync.isAsyncEnabled() || !(ref instanceof ThreadReferenceImpl)) {
      return CompletableFuture.completedFuture(calcIcon(threadProxy, current));
    }
    ThreadReferenceImpl threadReference = ((ThreadReferenceImpl)ref);
    if (current) {
      return calcThreadIconAsync(threadReference, true);
    }
    return threadReference.isAtBreakpointAsync().thenCompose(r -> {
      if (r) {
        return CompletableFuture.completedFuture(AllIcons.Debugger.ThreadAtBreakpoint);
      }
      else {
        return calcThreadIconAsync(threadReference, false);
      }
    });
  }

  private static CompletableFuture<Icon> calcThreadIconAsync(ThreadReferenceImpl threadReference, boolean current) {
    return threadReference.isSuspendedAsync().thenApply(suspended -> {
      if (suspended) {
        if (current) {
          return AllIcons.Debugger.ThreadCurrent;
        }
        else {
          return AllIcons.Debugger.ThreadSuspended;
        }
      }
      return AllIcons.Debugger.ThreadRunning;
    });
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
        myTopFrames = createStackFrames(frame);
      }
    }
    catch (EvaluateException e) {
      LOG.info(e);
    }
    finally {
      myTopFramesReady = true;
    }
  }

  /**
   * @deprecated Use {@link #createStackFrames(StackFrameProxyImpl)} instead.
   */
  @NotNull
  @Deprecated(forRemoval = true)
  public XStackFrame createStackFrame(@NotNull StackFrameProxyImpl stackFrameProxy) {
    return createStackFrames(stackFrameProxy).get(0);
  }

  @NotNull
  public List<XStackFrame> createStackFrames(@NotNull StackFrameProxyImpl stackFrameProxy) {
    StackFrameDescriptorImpl descriptor = new StackFrameDescriptorImpl(stackFrameProxy, myTracker);

    XStackFrame topFrame = ContainerUtil.getFirstItem(myTopFrames);
    if (descriptor.getUiIndex() == 1 && topFrame instanceof JavaStackFrame) {
      Method method = descriptor.getMethod();
      if (method != null) {
        ((JavaStackFrame)topFrame).getDescriptor().putUserData(BreakpointIntentionAction.CALLER_KEY, DebuggerUtilsEx.methodKey(method));
      }
    }

    List<XStackFrame> customFrames = myDebugProcess.getPositionManager().createStackFrames(descriptor);
    if (!customFrames.isEmpty()) {
      return customFrames;
    }

    return Collections.singletonList(new JavaStackFrame(descriptor, true));
  }

  @NotNull
  private CompletableFuture<List<XStackFrame>> createStackFramesAsync(@NotNull StackFrameProxyImpl stackFrameProxy) {
    if (!Registry.is("debugger.async.frames")) {
      return CompletableFuture.completedFuture(createStackFrames(stackFrameProxy));
    }

    return StackFrameDescriptorImpl.createAsync(stackFrameProxy, myTracker)
      .thenApply(descriptor -> {
        XStackFrame topFrame = ContainerUtil.getFirstItem(myTopFrames);
        if (descriptor.getUiIndex() == 1 && topFrame instanceof JavaStackFrame) {
          Method method = descriptor.getMethod();
          if (method != null) {
            ((JavaStackFrame)topFrame).getDescriptor().putUserData(BreakpointIntentionAction.CALLER_KEY, DebuggerUtilsEx.methodKey(method));
          }
        }

        List<XStackFrame> customFrames = myDebugProcess.getPositionManager().createStackFrames(descriptor);
        if (!customFrames.isEmpty()) {
          return customFrames;
        }

        return Collections.singletonList(new JavaStackFrame(descriptor, true));
      });
  }

  @Nullable
  @Override
  public XStackFrame getTopFrame() {
    assert myTopFramesReady : "Top frame must be already calculated here";
    return ContainerUtil.getFirstItem(myTopFrames);
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
          container.errorOccurred(JavaDebuggerBundle.message("frame.panel.thread.finished"));
        }
        // isCollected is not needed as ObjectCollectedException was handled in status call
        else if (/*!myThreadProxy.isCollected() && */myDebugProcess.getSuspendManager().isSuspended(myThreadProxy)) {
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
          container.errorOccurred(JavaDebuggerBundle.message("frame.panel.frames.not.available"));
        }
      }
    });
  }

  private class AppendFrameCommand extends SuspendContextCommandImpl {
    private final Iterator<StackFrameProxyImpl> myStackFramesIterator;
    private final XStackFrameContainer myContainer;
    private int myAdded;
    private final int mySkip;
    private final List<? extends StackFrameItem> myAsyncStack;

    AppendFrameCommand(SuspendContextImpl suspendContext,
                       Iterator<StackFrameProxyImpl> stackFramesIterator,
                       XStackFrameContainer container,
                       int added,
                       int skip,
                       List<? extends StackFrameItem> asyncStack) {
      super(suspendContext);
      myStackFramesIterator = stackFramesIterator;
      myContainer = container;
      myAdded = added;
      mySkip = skip;
      myAsyncStack = asyncStack;
    }

    @Override
    public Priority getPriority() {
      return myAdded <= StackFrameProxyImpl.FRAMES_BATCH_MAX ? Priority.NORMAL : Priority.LOW;
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
        CompletableFuture<List<XStackFrame>> framesAsync;
        boolean first = myAdded == 0;
        frameProxy = myStackFramesIterator.next();
        if (first && myTopFramesReady) {
          framesAsync = CompletableFuture.completedFuture(myTopFrames);
        }
        else {
          framesAsync = createStackFramesAsync(frameProxy).thenApply(fs -> {
            if (first && !myTopFramesReady) {
              myTopFrames = fs;
              myTopFramesReady = true;
            }
            return fs;
          });
        }

        framesAsync.thenAccept(frames -> {
          for (XStackFrame frame : ContainerUtil.notNullize(frames)) {
            if (first || showFrame(frame)) {
              if (frame instanceof JavaStackFrame) {
                ((JavaStackFrame)frame).getDescriptor().updateRepresentationNoNotify(null, () -> {
                  // repaint on icon change
                  myContainer.addStackFrames(Collections.emptyList(), !myStackFramesIterator.hasNext());
                });
              }
              addFrameIfNeeded(frame, false);
            }
          }

          // replace the rest with the related stack (if available)
          if (myAsyncStack != null) {
            appendRelatedStack(myAsyncStack);
            return;
          }

          List<StackFrameItem> relatedStack = null;
          XStackFrame topFrame = ContainerUtil.getFirstItem(frames);
          if (AsyncStacksToggleAction.isAsyncStacksEnabled(
            (XDebugSessionImpl)suspendContext.getDebugProcess().getXdebugProcess().getSession()) &&
              topFrame instanceof JavaStackFrame) {
            relatedStack =
              AsyncStackTraceProvider.EP.computeSafeIfAny(p -> p.getAsyncStackTrace(((JavaStackFrame)topFrame), suspendContext));
            if (relatedStack != null) {
              appendRelatedStack(relatedStack);
              return;
            }
            // append agent stack after the next frame
            relatedStack = AsyncStacksUtils.getAgentRelatedStack(frameProxy, suspendContext);
          }

          myDebugProcess.getManagerThread().schedule(
            new AppendFrameCommand(suspendContext, myStackFramesIterator, myContainer, myAdded, mySkip, relatedStack));
        }).exceptionally(throwable -> DebuggerUtilsAsync.logError(throwable));
      }
      else {
        myContainer.addStackFrames(Collections.emptyList(), true);
      }
    }

    void appendRelatedStack(@NotNull List<? extends StackFrameItem> asyncStack) {
      int i = 0;
      boolean separator = true;
      for (StackFrameItem stackFrame : asyncStack) {
        if (myContainer.isObsolete()) return;
        if (i > AsyncStacksUtils.getMaxStackLength()) {
          addFrameIfNeeded(new XStackFrame() {
            @Override
            public void customizePresentation(@NotNull ColoredTextContainer component) {
              component.append(JavaDebuggerBundle.message("label.too.many.frames.rest.truncated"),
                               SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
            }
          }, true);
          return;
        }
        i++;
        if (stackFrame == null) {
          separator = true;
          continue;
        }
        XStackFrame newFrame = stackFrame.createFrame(myDebugProcess);
        if (newFrame != null && showFrame(newFrame)) {
          StackFrameItem.setWithSeparator(newFrame, separator);
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

  private static @NlsContexts.ListItem String calcRepresentation(ThreadReferenceProxyImpl thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    String name = thread.name();
    ThreadGroupReferenceProxyImpl gr = thread.threadGroupProxy();
    final String grname = (gr != null) ? gr.name() : null;
    final String threadStatusText = DebuggerUtilsEx.getThreadStatusText(thread.status());
    //noinspection HardCodedStringLiteral
    if (grname != null && !"SYSTEM".equalsIgnoreCase(grname)) {
      return JavaDebuggerBundle.message("label.thread.node.in.group", name, thread.uniqueID(), threadStatusText, grname);
    }
    return JavaDebuggerBundle.message("label.thread.node", name, thread.uniqueID(), threadStatusText);
  }

  private static @NlsContexts.ListItem CompletableFuture<String> calcRepresentationAsync(ThreadReferenceProxyImpl thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    ThreadReference ref = thread.getThreadReference();
    if (!DebuggerUtilsAsync.isAsyncEnabled() || !(ref instanceof ThreadReferenceImpl)) {
      return CompletableFuture.completedFuture(calcRepresentation(thread));
    }
    ThreadReferenceImpl threadReference = ((ThreadReferenceImpl)ref);
    CompletableFuture<String> nameFuture = threadReference.nameAsync();
    CompletableFuture<String> groupNameFuture = threadReference.threadGroupAsync().thenCompose(gr -> {
      if (gr instanceof ThreadGroupReferenceImpl) {
        return ((ThreadGroupReferenceImpl)gr).nameAsync();
      }
      return CompletableFuture.completedFuture(null);
    });
    CompletableFuture<String> statusTextFuture = threadReference.statusAsync().thenApply(DebuggerUtilsEx::getThreadStatusText);

    long uniqueID = threadReference.uniqueID();
    return DebuggerUtilsAsync.reschedule(groupNameFuture).thenCompose(grname -> {
      return nameFuture.thenCombine(statusTextFuture, (name, threadStatusText) -> {
        if (grname != null && !"SYSTEM".equalsIgnoreCase(grname)) {
          return JavaDebuggerBundle.message("label.thread.node.in.group", name, uniqueID, threadStatusText, grname);
        }
        return JavaDebuggerBundle.message("label.thread.node", name, uniqueID, threadStatusText);
      });
    });
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
