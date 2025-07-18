// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.feedback.UsageTracker;
import com.intellij.debugger.impl.DebugUtilsKt;
import com.intellij.debugger.impl.DebuggerContextImpl;
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
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.frame.XFramesView;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import com.jetbrains.jdi.ThreadGroupReferenceImpl;
import com.jetbrains.jdi.ThreadReferenceImpl;
import com.sun.jdi.Method;
import com.sun.jdi.ThreadReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
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
    if (!DebuggerUtilsAsync.isAsyncEnabled() || !(ref instanceof ThreadReferenceImpl threadReference)) {
      return CompletableFuture.completedFuture(calcIcon(threadProxy, current));
    }
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

  @ApiStatus.Internal
  public @NotNull ThreadReferenceProxyImpl getThreadProxy() {
    return myThreadProxy;
  }

  public final void initTopFrame() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      StackFrameProxyImpl frame = myThreadProxy.frame(0);
      if (frame != null) {
        myTopFrames = createStackFrames(frame);
      }
      UsageTracker.topFrameInitialized(ContainerUtil.getFirstItem(myTopFrames));
    }
    catch (EvaluateException e) {
      LOG.info(e);
    }
    finally {
      myTopFramesReady = true;
    }
  }

  public @NotNull List<XStackFrame> createStackFrames(@NotNull StackFrameProxyImpl stackFrameProxy) {
    return createFrames(new StackFrameDescriptorImpl(myTracker, stackFrameProxy));
  }

  private @NotNull CompletableFuture<List<XStackFrame>> createStackFramesAsync(@NotNull StackFrameProxyImpl stackFrameProxy) {
    if (!Registry.is("debugger.async.frames")) {
      return CompletableFuture.completedFuture(createStackFrames(stackFrameProxy));
    }

    return StackFrameDescriptorImpl.createAsync(stackFrameProxy, myTracker)
      .thenCompose(this::createFramesAsync);
  }

  private @NotNull List<XStackFrame> createFrames(StackFrameDescriptorImpl descriptor) {
    markCallerFrame(descriptor);

    List<XStackFrame> customFrames = myDebugProcess.getPositionManager().createStackFrames(descriptor);
    if (customFrames != null) {
      return customFrames;
    }

    return Collections.singletonList(new JavaStackFrame(descriptor, true));
  }

  private @NotNull CompletableFuture<@NotNull List<XStackFrame>> createFramesAsync(StackFrameDescriptorImpl descriptor) {
    markCallerFrame(descriptor);

    return myDebugProcess.getPositionManager().createStackFramesAsync(descriptor)
      .thenApply(customFrames -> {
        if (customFrames != null) {
          return customFrames;
        }
        return Collections.singletonList(new JavaStackFrame(descriptor, true));
      });
  }

  private void markCallerFrame(StackFrameDescriptorImpl descriptor) {
    XStackFrame topFrame = ContainerUtil.getFirstItem(myTopFrames);
    if (descriptor.getUiIndex() == 1 && topFrame instanceof JavaStackFrame) {
      Method method = descriptor.getMethod();
      if (method != null) {
        ((JavaStackFrame)topFrame).getDescriptor().putUserData(BreakpointIntentionAction.CALLER_KEY, DebuggerUtilsEx.methodKey(method));
      }
    }
  }

  @Override
  public @Nullable XStackFrame getTopFrame() {
    return ContainerUtil.getFirstItem(myTopFrames);
  }

  @Override
  public void computeStackFrames(final int firstFrameIndex, final XStackFrameContainer container) {
    if (container.isObsolete()) return;
    DebuggerContextImpl debuggerContext = myDebugProcess.getDebuggerContext();
    Objects.requireNonNull(debuggerContext.getManagerThread()).schedule(new DebuggerContextCommandImpl(debuggerContext, myThreadProxy) {
      @Override
      public @NotNull Priority getPriority() {
        return Priority.NORMAL;
      }

      @Override
      public void threadAction(@NotNull SuspendContextImpl suspendContext) {
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
              suspendContext.getManagerThread().schedule(
                new AppendFrameCommand(suspendContext, iterator, container, added, firstFrameIndex));
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
    private final List<? extends StackFrameItem> myCreationStack;
    private int myAddedAsync;
    private boolean mySeparator;

    /**
     * Current hidden frames since the last shown one.
     */
    private final List<XStackFrame> myHiddenFrames;

    private AppendFrameCommand(SuspendContextImpl suspendContext,
                       @Nullable Iterator<StackFrameProxyImpl> stackFramesIterator,
                       XStackFrameContainer container,
                       int added,
                       int skip,
                       List<XStackFrame> hiddenFrames,
                       @Nullable List<? extends StackFrameItem> asyncStack,
                       @Nullable List<? extends StackFrameItem> creationStack,
                       int addedAsync,
                       boolean separator) {
      super(suspendContext);
      myStackFramesIterator = stackFramesIterator;
      myContainer = container;
      myAdded = added;
      mySkip = skip;
      myHiddenFrames = hiddenFrames;
      myAsyncStack = asyncStack;
      myCreationStack = creationStack;
      myAddedAsync = addedAsync;
      mySeparator = separator;
    }

    AppendFrameCommand(@NotNull SuspendContextImpl suspendContext,
                       Iterator<StackFrameProxyImpl> iterator,
                       XStackFrameContainer container,
                       int added,
                       int firstFrameIndex) {
      this(suspendContext, iterator, container, added, firstFrameIndex, new SmartList<>(), null, null, 0, true);
    }

    @Override
    public Priority getPriority() {
      return myAdded <= StackFrameProxyImpl.FRAMES_BATCH_MAX ? Priority.NORMAL : Priority.LOW;
    }

    private void flushHiddenFrames() {
      if (!XFramesView.shouldFoldHiddenFrames()) return;

      if (!myHiddenFrames.isEmpty()) {
        var placeholder = new XFramesView.HiddenStackFramesItem(myHiddenFrames);
        myAdded++;
        myContainer.addStackFrames(Collections.singletonList(placeholder), false);
        myHiddenFrames.clear();
      }
    }

    private void rememberHiddenFrame(XStackFrame frame) {
      if (!XFramesView.shouldFoldHiddenFrames() || myAdded < mySkip) return;

      myHiddenFrames.add(frame);
    }

    private void addStackFrames(List<XStackFrame> frames, boolean last) {
      flushHiddenFrames();
      myAdded += frames.size();
      myContainer.addStackFrames(frames, last);
      if (last) {
        myTracker.finish();
      }
    }

    private boolean addFrameIfNeeded(XStackFrame frame, boolean last) {
      if (myAdded >= mySkip) {
        addStackFrames(Collections.singletonList(frame), last);
        return true;
      }
      if (last) {
        addStackFrames(Collections.emptyList(), true);
      }
      return false;
    }

    @Override
    public void contextAction(@NotNull SuspendContextImpl suspendContext) {
      if (myContainer.isObsolete()) return;
      if (myStackFramesIterator != null && myStackFramesIterator.hasNext()) {
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
                  addStackFrames(Collections.emptyList(), !myStackFramesIterator.hasNext());
                });
              }
              addFrameIfNeeded(frame, false);
            }
            else {
              rememberHiddenFrame(frame);
            }
          }

          // replace the rest with the related stack (if available)
          if (myAsyncStack != null) {
            schedule(suspendContext, null, myAsyncStack, null, true);
            return;
          }

          List<StackFrameItem> relatedStack = null;
          var creationStack = myCreationStack;
          XStackFrame topFrame = ContainerUtil.getFirstItem(frames);
          JavaDebugProcess xdebugProcess = suspendContext.getDebugProcess().getXdebugProcess();
          if (xdebugProcess != null &&
              AsyncStacksUtils.isAsyncStacksEnabled((XDebugSessionImpl)xdebugProcess.getSession()) &&
              topFrame instanceof JavaStackFrame frame) {
            if (creationStack == null) {
              creationStack = DebugUtilsKt.computeSafeIfAny(CreationStackTraceProvider.EP,
                                                                 p -> p.getCreationStackTrace(frame, suspendContext));
            }
            relatedStack = DebugUtilsKt.computeSafeIfAny(AsyncStackTraceProvider.EP,
                                                              p -> p.getAsyncStackTrace(frame, suspendContext));
            if (relatedStack != null) {
              schedule(suspendContext, null, relatedStack, null, true);
              return;
            }
            // append agent stack after the next frame
            relatedStack = AsyncStacksUtils.getAgentRelatedStack(frameProxy, suspendContext);
          }

          schedule(suspendContext, myStackFramesIterator, relatedStack, creationStack, false);
        }).exceptionally(throwable -> DebuggerUtilsAsync.logError(throwable));
      }
      else if (myAsyncStack != null && myAddedAsync < myAsyncStack.size()) {
        appendRelatedStack(suspendContext, myAsyncStack.subList(myAddedAsync, myAsyncStack.size()));
      }
      else if (myCreationStack != null && myAddedAsync < myCreationStack.size()) {
        appendRelatedStack(suspendContext, myCreationStack.subList(myAddedAsync, myCreationStack.size()));
      }
      else {
        addStackFrames(Collections.emptyList(), true);
      }
    }

    private void schedule(@NotNull SuspendContextImpl suspendContext,
                          @Nullable Iterator<StackFrameProxyImpl> stackFramesIterator,
                          @Nullable List<? extends StackFrameItem> asyncStackFrames,
                          @Nullable List<? extends StackFrameItem> creationStackFrames,
                          boolean separator) {
      suspendContext.getManagerThread().schedule(
        new AppendFrameCommand(suspendContext, stackFramesIterator, myContainer,
                               myAdded, mySkip, myHiddenFrames, asyncStackFrames, creationStackFrames, myAddedAsync, separator));
    }

    void appendRelatedStack(@NotNull SuspendContextImpl suspendContext, List<? extends StackFrameItem> asyncStack) {
      for (StackFrameItem stackFrame : asyncStack) {
        if (myAddedAsync > AsyncStacksUtils.getMaxStackLength()) {
          addFrameIfNeeded(new XStackFrame() {
            @Override
            public void customizePresentation(@NotNull ColoredTextContainer component) {
              component.append(JavaDebuggerBundle.message("label.too.many.frames.rest.truncated"),
                               SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
            }
          }, true);
          return;
        }
        myAddedAsync++;
        if (stackFrame == null) {
          mySeparator = true;
          continue;
        }
        myDebugProcess.getPositionManager().getSourcePositionFuture(stackFrame.location()).thenAccept(sourcePosition -> {
          XStackFrame newFrame = stackFrame.createFrame(myDebugProcess, sourcePosition);
          appendFrame(suspendContext, newFrame);
        });
        return;
      }
    }

    private void appendFrame(@NotNull SuspendContextImpl suspendContext, XStackFrame newFrame) {
      if (newFrame != null) {
        if (showFrame(newFrame)) {
          if (mySeparator) {
            flushHiddenFrames();
            StackFrameItem.setWithSeparator(newFrame);
          }
          if (addFrameIfNeeded(newFrame, false)) {
            // No need to propagate the separator further, because it was added.
            mySeparator = false;
          }
        }
        else { // Hidden frame case.
          var frameHasSeparator = StackFrameItem.hasSeparatorAbove(newFrame);
          if (XFramesView.shouldFoldHiddenFrames()) {
            if (mySeparator || frameHasSeparator) {
              flushHiddenFrames();
              if (!frameHasSeparator) {
                // The separator for this hidden frame will be used as the placeholder separator.
                StackFrameItem.setWithSeparator(newFrame);
              }
              mySeparator = false;
            }
            rememberHiddenFrame(newFrame);
          }
          else if (!mySeparator && frameHasSeparator) {
            // Frame has a separator, but it wasn't added; we need to propagate the separator further.
            mySeparator = true;
          }
        }
      }
      schedule(suspendContext, null, myAsyncStack, myCreationStack, mySeparator);
    }
  }

  private static boolean showFrame(@NotNull XStackFrame frame) {
    if (!XDebuggerSettingsManager.getInstance().getDataViewSettings().isShowLibraryStackFrames() &&
        frame instanceof JVMStackFrameInfoProvider info) {
      return !info.shouldHide();
    }
    return true;
  }

  private static @NlsContexts.ListItem String calcRepresentation(ThreadReferenceProxyImpl thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    String name = thread.name();
    ThreadGroupReferenceProxyImpl gr = thread.threadGroupProxy();
    final String grname = (gr != null) ? gr.name() : null;
    final String threadStatusText = DebuggerUtilsEx.getThreadStatusText(thread.status());
    if (grname != null && !"SYSTEM".equalsIgnoreCase(grname)) {
      return JavaDebuggerBundle.message("label.thread.node.in.group", name, thread.uniqueID(), threadStatusText, grname);
    }
    return JavaDebuggerBundle.message("label.thread.node", name, thread.uniqueID(), threadStatusText);
  }

  private static @NlsContexts.ListItem CompletableFuture<String> calcRepresentationAsync(ThreadReferenceProxyImpl thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    ThreadReference ref = thread.getThreadReference();
    if (!DebuggerUtilsAsync.isAsyncEnabled() || !(ref instanceof ThreadReferenceImpl threadReference)) {
      return CompletableFuture.completedFuture(calcRepresentation(thread));
    }
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
