// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.jdi.ClassesByNameProvider;
import com.intellij.debugger.jdi.GeneratedLocation;
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
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * @author egor
 */
public class AsyncStacksUtils {
  private static final Logger LOG = Logger.getInstance(AsyncStacksUtils.class);
  // TODO: obtain CaptureStorage fqn from the class somehow
  public static final String CAPTURE_STORAGE_CLASS_NAME = "com.intellij.rt.debugger.agent.CaptureStorage";
  public static final String CAPTURE_AGENT_CLASS_NAME = "com.intellij.rt.debugger.agent.CaptureAgent";

  public static boolean isAgentEnabled() {
    return DebuggerSettings.getInstance().INSTRUMENTING_AGENT;
  }

  @Nullable
  public static List<StackFrameItem> getAgentRelatedStack(JavaStackFrame frame, @NotNull SuspendContextImpl suspendContext) {
    if (isAgentEnabled() && suspendContext.getDebugProcess().isEvaluationPossible(suspendContext)) {
      Location location = frame.getDescriptor().getLocation();
      if (location != null) {
        Method method = DebuggerUtilsEx.getMethod(location);
        // TODO: use com.intellij.rt.debugger.agent.CaptureStorage.GENERATED_INSERT_METHOD_POSTFIX
        if (method != null && method.name().endsWith("$$$capture")) {
          try {
            return getProcessCapturedStack(new EvaluationContextImpl(suspendContext, frame.getStackFrameProxy()));
          }
          catch (EvaluateException e) {
            LOG.error(e);
          }
        }
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
          methodPair = Pair.create(captureClass, captureClass.methodsByName("getCurrentCapturedStack").get(0));
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

    VirtualMachineProxyImpl virtualMachineProxy = process.getVirtualMachineProxy();
    List<Value> args = Collections.singletonList(virtualMachineProxy.mirrorOf(getMaxStackLength()));
    Pair<ClassType, Method> finalMethodPair = methodPair;
    Value resArray = evaluationContext.computeAndKeep(
      () -> process.invokeMethod(evaluationContext, finalMethodPair.first, finalMethodPair.second,
                                 args, ObjectReference.INVOKE_SINGLE_THREADED, true));
    if (resArray instanceof ArrayReference) {
      List<Value> values = ((ArrayReference)resArray).getValues();
      List<StackFrameItem> res = new ArrayList<>(values.size());
      ClassesByNameProvider classesByName = ClassesByNameProvider.createCache(virtualMachineProxy.allClasses());
      for (Value value : values) {
        if (value == null) {
          res.add(null);
        }
        else {
          List<Value> values1 = ((ArrayReference)value).getValues();
          String className = getStringRefValue((StringReference)values1.get(0));
          String methodName = getStringRefValue((StringReference)values1.get(2));
          int line = Integer.parseInt(((StringReference)values1.get(3)).value());
          Location location = findLocation(process, ContainerUtil.getFirstItem(classesByName.get(className)), methodName, line);
          res.add(new ProcessStackFrameItem(location, className, methodName));
        }
      }
      return res;
    }
    return null;
  }

  private static String getStringRefValue(StringReference ref) {
    return ref != null ? ref.value() : null;
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
      Properties properties = CaptureSettingsProvider.getPointsProperties();
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

  private static class ProcessStackFrameItem extends StackFrameItem {
    final String myClass;
    final String myMethod;

    ProcessStackFrameItem(Location location, String aClass, String method) {
      super(location, null);
      myClass = aClass;
      myMethod = method;
    }

    @NotNull
    @Override
    public String path() {
      return myClass;
    }

    @NotNull
    @Override
    public String method() {
      return myMethod;
    }

    @Override
    public String toString() {
      return myClass + "." + myMethod + ":" + line();
    }
  }

  private static Location findLocation(DebugProcessImpl debugProcess, ReferenceType type, String methodName, int line) {
    if (type != null && line >= 0) {
      try {
        Location location = type.locationsOfLine(DebugProcess.JAVA_STRATUM, null, line).stream()
                                .filter(l -> l.method().name().equals(methodName))
                                .findFirst().orElse(null);
        if (location != null) {
          return location;
        }
      }
      catch (AbsentInformationException ignored) {
      }
    }
    return new GeneratedLocation(debugProcess, type, methodName, line);
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
        Method method = captureClass.methodsByName("addCapturePoints").get(0);
        if (method != null) {
          StringWriter writer = new StringWriter();
          try {
            properties.store(writer, null);
            List<StringReference> args =
              Collections.singletonList(DebuggerUtilsEx.mirrorOfString(writer.toString(), process.getVirtualMachineProxy(), evalContext));
            process.invokeMethod(evaluationContext, captureClass, method, args, ObjectReference.INVOKE_SINGLE_THREADED, true);
          }
          catch (Exception e) {
            LOG.error(e);
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
