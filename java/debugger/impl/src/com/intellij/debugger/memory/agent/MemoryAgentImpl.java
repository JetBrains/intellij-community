// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.ReferringObjectsProvider;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.memory.agent.parsers.BooleanParser;
import com.intellij.debugger.memory.agent.parsers.GcRootsPathsParser;
import com.intellij.debugger.memory.agent.parsers.LongValueParser;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class MemoryAgentImpl implements MemoryAgent {
  public static final String PROXY_CLASS_NAME = "com.intellij.memory.agent.proxy.IdeaNativeAgentProxy";

  private static final Logger LOG = Logger.getInstance(MemoryAgentImpl.class);

  private static final String SIZE_OF_SINGLE_OBJECT_METHOD_NAME = "size";
  private static final String SIZE_OF_OBJECTS_METHOD_NAME = "sizes";
  private static final String GARBAGE_COLLECTOR_ROOTS_METHOD_NAME = "gcRoots";

  private final DebugProcessImpl myDebugProcess;
  private final ClassType myProxyClassType;

  private volatile boolean myIsLoaded;
  private volatile boolean myCanFindGcRoots;
  private volatile boolean myCanEstimateObjectSize;
  private volatile boolean myCanEstimateObjectsSizes;

  public MemoryAgentImpl(@NotNull DebugProcessImpl debugProcess, @NotNull ClassType reference) {
    myDebugProcess = debugProcess;
    myProxyClassType = reference;
  }

  public void initializeCapabilities() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myIsLoaded = checkCapability("isLoaded");
    myCanFindGcRoots = checkCapability("canFindGcRoots");
    myCanEstimateObjectSize = checkCapability("canEstimateObjectSize");
    myCanEstimateObjectsSizes = checkCapability("canEstimateObjectsSizes");
  }

  @Override
  public boolean canEvaluateObjectSize() {
    return myCanEstimateObjectSize;
  }

  @Override
  public long evaluateObjectSize(@NotNull ObjectReference reference) throws EvaluateException {
    if (!canEvaluateObjectSize()) throw new UnsupportedOperationException();
    Value result = callMethod(SIZE_OF_SINGLE_OBJECT_METHOD_NAME, Collections.singletonList(reference));
    return result != null ? new LongValueParser().parse(result) : -1;
  }

  @Override
  public boolean canEvaluateObjectsSizes() {
    return myCanEstimateObjectsSizes;
  }

  @Override
  public List<Long> evaluateObjectsSizes(@NotNull List<ObjectReference> references) throws EvaluateException {
    if (!canEvaluateObjectsSizes()) throw new UnsupportedOperationException();
    Value result = callMethod(SIZE_OF_OBJECTS_METHOD_NAME, references);
    // TODO: Implement method and conversion
    return Collections.emptyList();
  }

  @Override
  public boolean canFindGcRoots() {
    return myCanFindGcRoots;
  }

  @Nullable
  @Override
  public ReferringObjectsProvider findGcRoots(@NotNull ObjectReference reference) throws EvaluateException {
    if (!canFindGcRoots()) throw new UnsupportedOperationException();

    Value value = callMethod(GARBAGE_COLLECTOR_ROOTS_METHOD_NAME, Collections.singletonList(reference));
    return value == null ? null : new GcRootsPathsParser().parse(value);
  }

  @Override
  public boolean isLoaded() {
    return myIsLoaded;
  }

  private boolean checkCapability(String methodName) {
    try {
      Value value = callMethod(methodName, Collections.emptyList());
      return value != null && BooleanParser.INSTANCE.parse(value);
    }
    catch (EvaluateException e) {
      return false;
    }
  }

  @Nullable
  private Value callMethod(@NotNull String methodName,
                           @NotNull List<? extends Value> args) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    List<Method> methods = myProxyClassType.methodsByName(methodName);
    if (methods.isEmpty()) {
      LOG.warn("Method \"" + methodName + "\" not found");
      return null;
    }
    if (methods.size() > 1) {
      LOG.warn("Too many methods \"" + methodName + "\" found. Count: " + methods);
    }

    Method method = methods.get(0);
    if (!method.isStatic()) {
      LOG.error("Utility method should be static");
    }
    SuspendContextImpl suspendContext = myDebugProcess.getSuspendManager().getPausedContext();
    EvaluationContextImpl evaluationContext = new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy());
    return myDebugProcess.invokeMethod(evaluationContext, myProxyClassType, method, args);
  }
}
