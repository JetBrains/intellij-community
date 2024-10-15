// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.jdi.ClassesByNameProvider;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.debugger.settings.CaptureSettingsProvider;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public final class AsyncStacksUtils {
  private static final Logger LOG = Logger.getInstance(AsyncStacksUtils.class);
  // TODO: obtain CaptureStorage fqn from the class somehow
  public static final String CAPTURE_STORAGE_CLASS_NAME = "com.intellij.rt.debugger.agent.CaptureStorage";
  public static final String CAPTURE_AGENT_CLASS_NAME = "com.intellij.rt.debugger.agent.CaptureAgent";

  public static boolean isAgentEnabled() {
    return DebuggerSettings.getInstance().INSTRUMENTING_AGENT;
  }

  @Nullable
  public static List<StackFrameItem> getAgentRelatedStack(@NotNull StackFrameProxyImpl frame, @NotNull SuspendContextImpl suspendContext) {
    if (!isAgentEnabled() || !frame.threadProxy().equals(suspendContext.getThread())) { // only for the current thread for now
      return null;
    }
    try {
      Method method = DebuggerUtilsEx.getMethod(frame.location());
      // TODO: use com.intellij.rt.debugger.agent.CaptureStorage.GENERATED_INSERT_METHOD_POSTFIX
      if (method != null && method.name().endsWith("$$$capture")) {
        return getProcessCapturedStack(new EvaluationContextImpl(suspendContext, frame));
      }
    }
    catch (EvaluateException e) {
      ObjectReference targetException = e.getExceptionFromTargetVM();
      if (e.getCause() instanceof IncompatibleThreadStateException) {
        LOG.warn(e);
      }
      else if (targetException != null && DebuggerUtils.instanceOf(targetException.type(), "java.lang.StackOverflowError")) {
        LOG.warn(e);
      }
      else {
        LOG.error(e);
      }
    }
    return null;
  }

  private static final Key<Pair<ClassType, Method>> CAPTURE_STORAGE_METHOD = Key.create("CAPTURE_STORAGE_METHOD");
  private static final Pair<ClassType, Method> NO_CAPTURE_AGENT = Pair.empty();

  private static List<StackFrameItem> getProcessCapturedStack(EvaluationContextImpl evalContext)
    throws EvaluateException {
    EvaluationContextImpl evaluationContext = evalContext.withAutoLoadClasses(false);

    DebugProcessImpl process = evaluationContext.getDebugProcess();
    Pair<ClassType, Method> methodPair = process.getUserData(CAPTURE_STORAGE_METHOD);

    if (methodPair == null) {
      try {
        ClassType captureClass = (ClassType)process.findClass(evaluationContext, CAPTURE_STORAGE_CLASS_NAME, null);
        if (captureClass == null) {
          methodPair = NO_CAPTURE_AGENT;
          LOG.debug("Error loading debug agent", "agent class not found");
        }
        else {
          methodPair = Pair.create(captureClass, DebuggerUtils.findMethod(captureClass, "getCurrentCapturedStack", null));
        }
      }
      catch (EvaluateException e) {
        methodPair = NO_CAPTURE_AGENT;
        LOG.debug("Error loading debug agent", e);
      }
      putProcessUserData(CAPTURE_STORAGE_METHOD, methodPair, process);
    }

    if (methodPair == NO_CAPTURE_AGENT) {
      return null;
    }

    VirtualMachineProxyImpl virtualMachineProxy = evalContext.getVirtualMachineProxy();
    List<Value> args = Collections.singletonList(virtualMachineProxy.mirrorOf(getMaxStackLength()));
    Pair<ClassType, Method> finalMethodPair = methodPair;
    String value = DebuggerUtils.getInstance().processCollectibleValue(
      () -> process.invokeMethod(evaluationContext, finalMethodPair.first, finalMethodPair.second,
                                 args, ObjectReference.INVOKE_SINGLE_THREADED, true),
      result -> result instanceof StringReference ? ((StringReference)result).value() : null,
      evaluationContext);
    if (value != null) {
      List<StackFrameItem> res = new ArrayList<>();
      ClassesByNameProvider classesByName = ClassesByNameProvider.createCache(virtualMachineProxy.allClasses());
      try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(value.getBytes(StandardCharsets.ISO_8859_1)))) {
        while (dis.available() > 0) {
          StackFrameItem item = null;
          if (dis.readBoolean()) {
            String className = dis.readUTF();
            String methodName = dis.readUTF();
            int line = dis.readInt();
            Location location = DebuggerUtilsEx.findOrCreateLocation(process, classesByName, className, methodName, line);
            item = new StackFrameItem(location, null);
          }
          res.add(item);
        }
        return res;
      }
      catch (Exception e) {
        DebuggerUtilsImpl.logError(e);
      }
    }
    return null;
  }

  public static void setupAgent(DebugProcessImpl process) {
    if (!isAgentEnabled()) {
      return;
    }

    // set debug mode
    if (Registry.is("debugger.capture.points.agent.debug")) {
      enableAgentDebug(process);
    }

    // add points
    if (DebuggerUtilsImpl.isRemote(process)) {
      Properties properties = CaptureSettingsProvider.getPointsProperties(process.getProject());
      if (!properties.isEmpty()) {
        process.addDebugProcessListener(new DebugProcessAdapterImpl() {
          @Override
          public void paused(SuspendContextImpl suspendContext) {
            if (process.isEvaluationPossible()) { // evaluation is possible
              try {
                StackCapturingLineBreakpoint.deleteAll(process);

                try {
                  addAgentCapturePoints(new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy()), properties);
                  process.removeDebugProcessListener(this);
                }
                finally {
                  process.onHotSwapFinished();
                  StackCapturingLineBreakpoint.createAll(process);
                }
              }
              catch (Exception e) {
                LOG.debug(e);
              }
            }
          }
        });
      }
    }
  }

  private static void enableAgentDebug(DebugProcessImpl process) {
    final RequestManagerImpl requestsManager = process.getRequestsManager();
    ClassPrepareRequestor requestor = new ClassPrepareRequestor() {
      @Override
      public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType) {
        try {
          requestsManager.deleteRequest(this);
          ((ClassType)referenceType).setValue(referenceType.fieldByName("DEBUG"), process.getVirtualMachineProxy().mirrorOf(true));
        }
        catch (Exception e) {
          LOG.warn("Error setting agent debug mode", e);
        }
      }
    };
    requestsManager.callbackOnPrepareClasses(requestor, CAPTURE_STORAGE_CLASS_NAME);
    try {
      ClassType captureClass = (ClassType)process.findClass(null, CAPTURE_STORAGE_CLASS_NAME, null);
      if (captureClass != null) {
        requestor.processClassPrepare(process, captureClass);
      }
    }
    catch (Exception e) {
      LOG.warn("Error setting agent debug mode", e);
    }
  }

  public static void addAgentCapturePoints(EvaluationContextImpl evalContext, Properties properties) {
    EvaluationContextImpl evaluationContext = evalContext.withAutoLoadClasses(false);
    DebugProcessImpl process = evaluationContext.getDebugProcess();
    try {
      ClassType captureClass = (ClassType)process.findClass(evaluationContext, CAPTURE_AGENT_CLASS_NAME, null);
      if (captureClass == null) {
        LOG.debug("Error loading debug agent", "agent class not found");
      }
      else {
        Method method = DebuggerUtils.findMethod(captureClass, "addCapturePoints", null);
        if (method != null) {
          StringWriter writer = new StringWriter();
          try {
            properties.store(writer, null);
            var stringArgs = DebuggerUtilsEx.mirrorOfString(writer.toString(), evalContext);
            List<StringReference> args = Collections.singletonList(stringArgs);
            process.invokeMethod(evaluationContext, captureClass, method, args, ObjectReference.INVOKE_SINGLE_THREADED, true);
          }
          catch (Exception e) {
            DebuggerUtilsImpl.logError(e);
          }
        }
      }
    }
    catch (EvaluateException e) {
      LOG.debug("Error loading debug agent", e);
    }
  }

  public static <T> void putProcessUserData(@NotNull Key<T> key, @Nullable T value, DebugProcessImpl debugProcess) {
    debugProcess.putUserData(key, value);
    debugProcess.addDebugProcessListener(new DebugProcessListener() {
      @Override
      public void processDetached(@NotNull DebugProcess process, boolean closedByUser) {
        process.putUserData(key, null);
      }
    });
  }

  public static int getMaxStackLength() {
    return Registry.intValue("debugger.async.stacks.max.depth", 500);
  }
}
