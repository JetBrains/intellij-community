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
package com.intellij.debugger.impl;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.ui.MessageCategory;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author lex
 */
class ReloadClassesWorker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.ReloadClassesWorker");
  /**
   * number of clasess that will be reloaded in one go. 
   * Such restriction is needed to deal with big number of classes being reloaded
   */
  private static final int CLASSES_CHUNK_SIZE = 100; 
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
    if(modifiedClasses == null || modifiedClasses.size() == 0) {
      myProgress.addMessage(myDebuggerSession, MessageCategory.INFORMATION, DebuggerBundle.message("status.hotswap.loaded.classes.up.to.date"));
      return;
    }

    final DebugProcessImpl debugProcess = getDebugProcess();
    final VirtualMachineProxyImpl virtualMachineProxy = debugProcess.getVirtualMachineProxy();
    if(virtualMachineProxy == null) {
      return;
    }

    final Project project = debugProcess.getProject();
    final BreakpointManager breakpointManager = (DebuggerManagerEx.getInstanceEx(project)).getBreakpointManager();
    breakpointManager.disableBreakpoints(debugProcess);
    
    //virtualMachineProxy.suspend();
           
    try {
      final Map<ReferenceType, byte[]> redefineMap = new HashMap<ReferenceType,byte[]>();
      int processedClassesCount = 0;
      final IOException[] _ex = new IOException[] {null};
      for (final String qualifiedName : modifiedClasses.keySet()) {
        processedClassesCount++;
        if (qualifiedName != null) {
          myProgress.setText(qualifiedName);
          myProgress.setFraction(processedClassesCount / (double)modifiedClasses.size());
        }
        _ex[0] = null;
        final HotSwapFile fileDescr = modifiedClasses.get(qualifiedName);
        final byte[] buffer = ApplicationManager.getApplication().runReadAction(new Computable<byte[]>() {
          public byte[] compute() {
            try {
              return fileDescr.file.contentsToByteArray();
            }
            catch (IOException e) {
              _ex[0] = e;
              return null;
            }
          }
        });
        if (buffer != null) {
          final List<ReferenceType> classes = virtualMachineProxy.classesByName(qualifiedName);
          for (final ReferenceType reference : classes) {
            redefineMap.put(reference, buffer);
          }
        }
        else {
          reportProblem(qualifiedName, _ex[0]);
        }
        if (redefineMap.size() >= CLASSES_CHUNK_SIZE) {
          // reload this portion of clasess and clear the map to free memory
          try {
            virtualMachineProxy.redefineClasses(redefineMap);
          }
          finally {
            redefineMap.clear();
          }
        }
      }
      if (redefineMap.size() > 0) {
        virtualMachineProxy.redefineClasses(redefineMap);
      }
      myProgress.setFraction(1);
            
      myProgress.addMessage(myDebuggerSession, MessageCategory.INFORMATION, DebuggerBundle.message("status.classes.reloaded", modifiedClasses.size()));
      if (LOG.isDebugEnabled()) {
        LOG.debug("classes reloaded");
      }
    }
    catch (Throwable e) {
      processException(e);
    }

    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (project.isDisposed()) {
          return;
        }
        final BreakpointManager breakpointManager = (DebuggerManagerEx.getInstanceEx(project)).getBreakpointManager();
        breakpointManager.reloadBreakpoints();
        debugProcess.getRequestsManager().clearWarnings();
        if (LOG.isDebugEnabled()) {
          LOG.debug("requests updated");
          LOG.debug("time stamp set");
        }
        myDebuggerSession.refresh(false);

        debugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
          protected void action() throws Exception {
            try {
              breakpointManager.enableBreakpoints(debugProcess);
            }
            catch (Exception e) {
              processException(e);
            }
            //try {
            //  virtualMachineProxy.resume();
            //}
            //catch (Exception e) {
            //  processException(e);
            //}
          }

          public Priority getPriority() {
            return Priority.HIGH;
          }
        });
      }
    });
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
}
