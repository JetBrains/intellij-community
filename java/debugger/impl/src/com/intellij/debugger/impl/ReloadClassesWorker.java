// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.actions.ThreadDumpAction;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.JavaExecutionStack;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.unscramble.ThreadState;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.MessageCategory;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.ReferenceType;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

class ReloadClassesWorker {
  private static final Logger LOG = Logger.getInstance(ReloadClassesWorker.class);
  private final @NotNull DebuggerSession myDebuggerSession;
  private final @NotNull HotSwapProgress myProgress;

  ReloadClassesWorker(@NotNull DebuggerSession session, @NotNull HotSwapProgress progress) {
    myDebuggerSession = session;
    myProgress = progress;
  }

  private void processException(@NotNull Throwable e) {
    if (e.getMessage() != null) {
      myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, e.getMessage());
    }

    if (e instanceof ProcessCanceledException) {
      myProgress.addMessage(myDebuggerSession, MessageCategory.INFORMATION, JavaDebuggerBundle.message("error.operation.canceled"));
      return;
    }

    String message =
      e instanceof UnsupportedOperationException
      ? JavaDebuggerBundle.message("error.operation.not.supported.by.vm")
      : e instanceof NoClassDefFoundError
        ? JavaDebuggerBundle.message("error.class.def.not.found", e.getLocalizedMessage())
        : e instanceof VerifyError
          ? JavaDebuggerBundle.message("error.verification.error", e.getLocalizedMessage())
          : e instanceof UnsupportedClassVersionError
            ? JavaDebuggerBundle.message("error.unsupported.class.version", e.getLocalizedMessage())
            : e instanceof ClassFormatError
              ? JavaDebuggerBundle.message("error.class.format.error", e.getLocalizedMessage())
              : e instanceof ClassCircularityError
                ? JavaDebuggerBundle.message("error.class.circularity.error", e.getLocalizedMessage())
                : JavaDebuggerBundle.message("error.exception.while.reloading", e.getClass().getName(), e.getLocalizedMessage());
    myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, message);
  }

  public void reloadClasses(@NotNull Map<@NotNull String, @NotNull HotSwapFile> modifiedClasses) {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    if (modifiedClasses.isEmpty()) {
      myProgress.addMessage(myDebuggerSession, MessageCategory.INFORMATION,
                            JavaDebuggerBundle.message("status.hotswap.loaded.classes.up.to.date"));
      return;
    }

    final DebugProcessImpl debugProcess = myDebuggerSession.getProcess();
    final VirtualMachineProxyImpl virtualMachineProxy = debugProcess.getVirtualMachineProxy();

    final Project project = debugProcess.getProject();
    final BreakpointManager breakpointManager = (DebuggerManagerEx.getInstanceEx(project)).getBreakpointManager();
    breakpointManager.disableBreakpoints(debugProcess);
    StackCapturingLineBreakpoint.deleteAll(debugProcess);

    //virtualMachineProxy.suspend();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Threads before hotswap:\n",
                StreamEx.of(ThreadDumpAction.buildThreadStates(virtualMachineProxy)).map(ThreadState::getStackTrace).joining("\n"));
    }

    if (Registry.is("debugger.resume.yourkit.threads")) {
      virtualMachineProxy.allThreads().stream()
        .filter(ThreadReferenceProxyImpl::isResumeOnHotSwap)
        .filter(ThreadReferenceProxyImpl::isSuspended)
        .forEach(t -> IntStream.range(0, t.getSuspendCount()).forEach(i -> t.resume()));
    }

    try {
      RedefineProcessor redefineProcessor = new RedefineProcessor(virtualMachineProxy);

      int processedEntriesCount = 0;
      for (Map.Entry<@NotNull String, @NotNull HotSwapFile> entry : modifiedClasses.entrySet()) {
        // stop if the process is finished already
        if (debugProcess.isDetached() || debugProcess.isDetaching()) {
          break;
        }
        if (redefineProcessor.mayCancel() && myProgress.isCancelled()) {
          break;
        }
        processedEntriesCount++;
        String qualifiedName = entry.getKey();
        myProgress.setText(qualifiedName);
        myProgress.setFraction(processedEntriesCount / (double)modifiedClasses.size());
        try {
          redefineProcessor.processClass(qualifiedName, entry.getValue().file);
        }
        catch (IOException e) {
          reportProblem(qualifiedName, e);
        }
      }

      if (redefineProcessor.mayCancel() && myProgress.isCancelled()) {
        return;
      }

      redefineProcessor.processPending();
      myProgress.setFraction(1);

      final int partiallyRedefinedClassesCount = redefineProcessor.getPartiallyRedefinedClassesCount();
      if (partiallyRedefinedClassesCount == 0) {
        myProgress.addMessage(
          myDebuggerSession, MessageCategory.INFORMATION,
          JavaDebuggerBundle.message("status.classes.reloaded", redefineProcessor.getProcessedClassesCount())
        );
      }
      else {
        final String message = JavaDebuggerBundle.message(
          "status.classes.not.all.versions.reloaded", partiallyRedefinedClassesCount, redefineProcessor.getProcessedClassesCount()
        );
        myProgress.addMessage(myDebuggerSession, MessageCategory.WARNING, message);
      }

      LOG.debug("classes reloaded");
    }
    catch (Throwable e) {
      processException(e);
    }

    debugProcess.onHotSwapFinished();

    DebuggerContextImpl context = myDebuggerSession.getContextManager().getContext();
    SuspendContextImpl suspendContext = context.getSuspendContext();
    if (suspendContext != null) {
      JavaExecutionStack stack = suspendContext.getActiveExecutionStack();
      if (stack != null) {
        stack.initTopFrame();
      }
    }

    breakpointManager.reloadBreakpoints();

    final Semaphore waitSemaphore = new Semaphore();
    waitSemaphore.down();
    SwingUtilities.invokeLater(() -> {
      try {
        if (!project.isDisposed()) {
          debugProcess.getRequestsManager().clearWarnings();
          if (LOG.isDebugEnabled()) {
            LOG.debug("requests updated");
            LOG.debug("time stamp set");
          }
          myDebuggerSession.refresh(false);

          XDebugSession session = myDebuggerSession.getXDebugSession();
          if (session != null) {
            session.rebuildViews();
          }
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }
      finally {
        waitSemaphore.up();
      }
    });

    waitSemaphore.waitFor();

    if (!project.isDisposed()) {
      try {
        breakpointManager.enableBreakpoints(debugProcess);
        StackCapturingLineBreakpoint.createAll(debugProcess);
      }
      catch (Exception e) {
        processException(e);
      }
    }
  }

  private void reportProblem(String qualifiedName, @Nullable Exception ex) {
    String reason = ex != null ? ex.getLocalizedMessage() : null;
    if (reason == null || reason.isEmpty()) {
      reason = JavaDebuggerBundle.message("error.io.error");
    }
    myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, qualifiedName + " : " + reason);
  }

  private static class RedefineProcessor {
    /**
     * number of classes that will be reloaded in one go.
     * Such restriction is needed to deal with big number of classes being reloaded
     */
    private static final int CLASSES_CHUNK_SIZE = 100;
    private final @NotNull VirtualMachineProxyImpl myVirtualMachineProxy;
    private final @NotNull Map<@NotNull ReferenceType, byte @NotNull []> myRedefineMap = new HashMap<>();
    private @Range(from = 0, to = Integer.MAX_VALUE) int myProcessedClassesCount;
    private @Range(from = 0, to = Integer.MAX_VALUE) int myPartiallyRedefinedClassesCount;

    RedefineProcessor(@NotNull VirtualMachineProxyImpl virtualMachineProxy) {
      myVirtualMachineProxy = virtualMachineProxy;
    }

    public void processClass(@NotNull String qualifiedName, @NotNull File file)
      throws IOException, LinkageError, UnsupportedOperationException {

      final List<ReferenceType> vmClasses = myVirtualMachineProxy.classesByName(qualifiedName);
      if (vmClasses.isEmpty()) {
        return;
      }

      final byte[] content = FileUtil.loadFileBytes(file);
      if (vmClasses.size() == 1) {
        myRedefineMap.put(vmClasses.get(0), content);
        if (myRedefineMap.size() >= CLASSES_CHUNK_SIZE) {
          processChunk();
        }
        return;
      }

      int redefinedVersionsCount = 0;
      LinkageError error = null;
      UnsupportedOperationException exception = null;
      for (ReferenceType vmClass : vmClasses) {
        try {
          myVirtualMachineProxy.redefineClasses(Collections.singletonMap(vmClass, content));
          redefinedVersionsCount++;
        }
        catch (LinkageError e) {
          error = e;
        }
        catch (UnsupportedOperationException e) {
          exception = e;
        }
      }
      if (redefinedVersionsCount == 0) {
        if (error != null) throw error;
        assert exception != null;
        throw exception;
      }

      if (redefinedVersionsCount < vmClasses.size()) {
        myPartiallyRedefinedClassesCount++;
      }
      myProcessedClassesCount++;
    }

    public void processPending() throws LinkageError, UnsupportedOperationException {
      if (myRedefineMap.size() > 0) {
        processChunk();
      }
    }

    private void processChunk() throws LinkageError, UnsupportedOperationException {
      // reload this portion of classes and clear the map to free memory
      try {
        myVirtualMachineProxy.redefineClasses(myRedefineMap);
        myProcessedClassesCount += myRedefineMap.size();
      }
      finally {
        myRedefineMap.clear();
      }
    }

    public boolean mayCancel() {
      // once at least one class has been actually reloaded, do not interrupt the whole process
      return myProcessedClassesCount == 0;
    }

    public int getProcessedClassesCount() {
      return myProcessedClassesCount;
    }

    public int getPartiallyRedefinedClassesCount() {
      return myPartiallyRedefinedClassesCount;
    }
  }
}
