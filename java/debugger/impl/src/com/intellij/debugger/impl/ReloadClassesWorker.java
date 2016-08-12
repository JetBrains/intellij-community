/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.impl;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.JavaExecutionStack;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.MessageCategory;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author lex
 */
class ReloadClassesWorker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.ReloadClassesWorker");
  private final DebuggerSession  myDebuggerSession;
  private final HotSwapProgress  myProgress;

  public ReloadClassesWorker(DebuggerSession session, HotSwapProgress progress) {
    myDebuggerSession = session;
    myProgress = progress;
  }

  private DebugProcessImpl getDebugProcess() {
    return myDebuggerSession.getProcess();
  }

  private void processException(Throwable e) {
    if (e.getMessage() != null) {
      myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, e.getMessage());
    }

    if (e instanceof ProcessCanceledException) {
      myProgress.addMessage(myDebuggerSession, MessageCategory.INFORMATION, DebuggerBundle.message("error.operation.canceled"));
      return;
    }

    if (e instanceof UnsupportedOperationException) {
      myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, DebuggerBundle.message("error.operation.not.supported.by.vm"));
    }
    else if (e instanceof NoClassDefFoundError) {
      myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, DebuggerBundle.message("error.class.def.not.found", e.getLocalizedMessage()));
    }
    else if (e instanceof VerifyError) {
      myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, DebuggerBundle.message("error.verification.error", e.getLocalizedMessage()));
    }
    else if (e instanceof UnsupportedClassVersionError) {
      myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, DebuggerBundle.message("error.unsupported.class.version", e.getLocalizedMessage()));
    }
    else if (e instanceof ClassFormatError) {
      myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, DebuggerBundle.message("error.class.format.error", e.getLocalizedMessage()));
    }
    else if (e instanceof ClassCircularityError) {
      myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, DebuggerBundle.message("error.class.circularity.error", e.getLocalizedMessage()));
    }
    else {
      myProgress.addMessage(
        myDebuggerSession, MessageCategory.ERROR,
        DebuggerBundle.message("error.exception.while.reloading", e.getClass().getName(), e.getLocalizedMessage())
      );
    }
  }

  public void reloadClasses(final Map<String, HotSwapFile> modifiedClasses) {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    if(modifiedClasses == null || modifiedClasses.size() == 0) {
      myProgress.addMessage(myDebuggerSession, MessageCategory.INFORMATION, DebuggerBundle.message("status.hotswap.loaded.classes.up.to.date"));
      return;
    }

    final DebugProcessImpl debugProcess = getDebugProcess();
    final VirtualMachineProxyImpl virtualMachineProxy = debugProcess.getVirtualMachineProxy();

    final Project project = debugProcess.getProject();
    final BreakpointManager breakpointManager = (DebuggerManagerEx.getInstanceEx(project)).getBreakpointManager();
    breakpointManager.disableBreakpoints(debugProcess);

    //virtualMachineProxy.suspend();

    try {
      RedefineProcessor redefineProcessor = new RedefineProcessor(virtualMachineProxy);

      int processedEntriesCount = 0;
      for (final Map.Entry<String, HotSwapFile> entry : modifiedClasses.entrySet()) {
        // stop if process is finished already
        if (debugProcess.isDetached() || debugProcess.isDetaching()) {
          break;
        }
        if (redefineProcessor.getProcessedClassesCount() == 0 && myProgress.isCancelled()) {
          // once at least one class has been actually reloaded, do not interrupt the whole process
          break;
        }
        processedEntriesCount++;
        final String qualifiedName = entry.getKey();
        if (qualifiedName != null) {
          myProgress.setText(qualifiedName);
          myProgress.setFraction(processedEntriesCount / (double)modifiedClasses.size());
        }
        try {
          redefineProcessor.processClass(qualifiedName, entry.getValue().file);
        }
        catch (IOException e) {
          reportProblem(qualifiedName, e);
        }
      }

      if (redefineProcessor.getProcessedClassesCount() == 0 && myProgress.isCancelled()) {
        // once at least one class has been actually reloaded, do not interrupt the whole process
        return;
      }

      redefineProcessor.processPending();
      myProgress.setFraction(1);

      final int partiallyRedefinedClassesCount = redefineProcessor.getPartiallyRedefinedClassesCount();
      if (partiallyRedefinedClassesCount == 0) {
        myProgress.addMessage(
          myDebuggerSession, MessageCategory.INFORMATION,
          DebuggerBundle.message("status.classes.reloaded", redefineProcessor.getProcessedClassesCount())
        );
      }
      else {
        final String message = DebuggerBundle.message(
          "status.classes.not.all.versions.reloaded", partiallyRedefinedClassesCount, redefineProcessor.getProcessedClassesCount()
        );
        myProgress.addMessage(myDebuggerSession, MessageCategory.WARNING, message);
      }

      LOG.debug("classes reloaded");
    }
    catch (Throwable e) {
      processException(e);
    }

    debugProcess.getPositionManager().clearCache();

    DebuggerContextImpl context = myDebuggerSession.getContextManager().getContext();
    SuspendContextImpl suspendContext = context.getSuspendContext();
    if (suspendContext != null) {
      XExecutionStack stack = suspendContext.getActiveExecutionStack();
      if (stack != null) {
        ((JavaExecutionStack)stack).initTopFrame();
      }
    }

    final Semaphore waitSemaphore = new Semaphore();
    waitSemaphore.down();
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> {
      try {
        if (!project.isDisposed()) {
          final BreakpointManager breakpointManager1 = (DebuggerManagerEx.getInstanceEx(project)).getBreakpointManager();
          breakpointManager1.reloadBreakpoints();
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
      }
      catch (Exception e) {
        processException(e);
      }
    }
  }

  private void reportProblem(final String qualifiedName, @Nullable Exception ex) {
    String reason = null;
    if (ex != null)  {
      reason = ex.getLocalizedMessage();
    }
    if (reason == null || reason.length() == 0) {
      reason = DebuggerBundle.message("error.io.error");
    }
    final StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
      buf.append(qualifiedName).append(" : ").append(reason);
      myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, buf.toString());
    }
    finally {
      StringBuilderSpinAllocator.dispose(buf);
    }
  }

  private static class RedefineProcessor {
    /**
     * number of classes that will be reloaded in one go.
     * Such restriction is needed to deal with big number of classes being reloaded
     */
    private static final int CLASSES_CHUNK_SIZE = 100;
    private final VirtualMachineProxyImpl myVirtualMachineProxy;
    private final Map<ReferenceType, byte[]> myRedefineMap = new HashMap<>();
    private int myProcessedClassesCount;
    private int myPartiallyRedefinedClassesCount;

    public RedefineProcessor(VirtualMachineProxyImpl virtualMachineProxy) {
      myVirtualMachineProxy = virtualMachineProxy;
    }

    public void processClass(String qualifiedName, File file) throws Throwable {
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
      Throwable error = null;
      for (ReferenceType vmClass : vmClasses) {
        try {
          myVirtualMachineProxy.redefineClasses(Collections.singletonMap(vmClass, content));
          redefinedVersionsCount++;
        }
        catch (Throwable t) {
          error = t;
        }
      }
      if (redefinedVersionsCount == 0) {
        throw error;
      }

      if (redefinedVersionsCount < vmClasses.size()) {
        myPartiallyRedefinedClassesCount++;
      }
      myProcessedClassesCount++;
    }

    private void processChunk() throws Throwable {
      // reload this portion of classes and clear the map to free memory
      try {
        myVirtualMachineProxy.redefineClasses(myRedefineMap);
        myProcessedClassesCount += myRedefineMap.size();
      }
      finally {
        myRedefineMap.clear();
      }
    }

    public void processPending() throws Throwable {
      if (myRedefineMap.size() > 0) {
        processChunk();
      }
    }

    public int getProcessedClassesCount() {
      return myProcessedClassesCount;
    }

    public int getPartiallyRedefinedClassesCount() {
      return myPartiallyRedefinedClassesCount;
    }
  }
}
