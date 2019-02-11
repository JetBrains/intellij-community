// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.memory.agent.parsers.BooleanParser;
import com.intellij.debugger.memory.agent.parsers.GcRootsPathsParser;
import com.intellij.debugger.memory.agent.parsers.LongArrayParser;
import com.intellij.debugger.memory.agent.parsers.LongValueParser;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MemoryAgentImpl implements MemoryAgent {
  public static final String PROXY_CLASS_NAME = "com.intellij.memory.agent.proxy.IdeaNativeAgentProxy";

  private static final Logger LOG = Logger.getInstance(MemoryAgentImpl.class);

  private static final String SIZE_OF_SINGLE_OBJECT_METHOD_NAME = "size";
  private static final String SIZE_OF_OBJECTS_METHOD_NAME = "estimateRetainedSize";
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
    return LongValueParser.INSTANCE.parse(result);
  }

  @Override
  public boolean canEvaluateObjectsSizes() {
    return myCanEstimateObjectsSizes;
  }

  @Override
  public long[] evaluateObjectsSizes(@NotNull List<ObjectReference> references) throws EvaluateException {
    if (!canEvaluateObjectsSizes()) throw new UnsupportedOperationException();
    Value result = callMethod(SIZE_OF_OBJECTS_METHOD_NAME, references, (args, context) -> {
      long start = System.currentTimeMillis();
      ArrayType longArray = (ArrayType)myDebugProcess.findClass(context, "java.lang.Object[]", context.getClassLoader());
      ArrayReference instancesArray = longArray.newInstance(references.size());
      instancesArray.setValues(references);
      LOG.info("Wrapping values with array took " + (System.currentTimeMillis() - start) + " ms");
      return Collections.singletonList(instancesArray);
    });
    return LongArrayParser.INSTANCE.parse(result).stream().mapToLong(Long::longValue).toArray();
  }

  @Override
  public boolean canFindGcRoots() {
    return myCanFindGcRoots;
  }

  @NotNull
  @Override
  public ReferringObjectsInfo findGcRoots(@NotNull ObjectReference reference, int limit) throws EvaluateException {
    if (!canFindGcRoots()) throw new UnsupportedOperationException();

    IntegerValue limitValue = myDebugProcess.getVirtualMachineProxy().mirrorOf(limit);
    Value value = callMethod(GARBAGE_COLLECTOR_ROOTS_METHOD_NAME, Arrays.asList(reference, limitValue));
    return GcRootsPathsParser.INSTANCE.parse(value);
  }

  @Override
  public boolean isLoaded() {
    return myIsLoaded;
  }

  private boolean checkCapability(String methodName) {
    try {
      Value value = callMethod(methodName, Collections.emptyList());
      return BooleanParser.INSTANCE.parse(value);
    }
    catch (EvaluateException e) {
      return false;
    }
  }

  @NotNull
  private Value callMethod(@NotNull String methodName,
                           @NotNull List<? extends Value> args) throws EvaluateException {
    return callMethod(methodName, args, ArgumentsTransformer.IDENTITY);
  }

  @NotNull
  private Value callMethod(@NotNull String methodName,
                           @NotNull List<? extends Value> args,
                           @NotNull ArgumentsTransformer transformer) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    long start = System.currentTimeMillis();
    List<Method> methods = myProxyClassType.methodsByName(methodName);
    if (methods.isEmpty()) {
      throw EvaluateExceptionUtil.createEvaluateException("Could not find method with such name: " + methodName);
    }
    if (methods.size() > 1) {
      throw EvaluateExceptionUtil.createEvaluateException("Too many methods \"" + methodName + "\" found. Count: " + methods.size());
    }

    Method method = methods.get(0);
    if (!method.isStatic()) {
      throw EvaluateExceptionUtil.createEvaluateException("Utility method should be static");
    }
    SuspendContextImpl suspendContext = myDebugProcess.getSuspendManager().getPausedContext();
    EvaluationContextImpl evaluationContext = new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy());
    List<? extends Value> transformedArgs;
    try {
      transformedArgs = transformer.transform(args, evaluationContext);
    }
    catch (Exception e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }

    Value result = myDebugProcess.invokeMethod(evaluationContext, myProxyClassType, method, transformedArgs);
    LOG.info("Memory agent's method \"" + methodName + "\" took " + (System.currentTimeMillis() - start) + " ms");
    return result;
  }

  @FunctionalInterface
  private interface ArgumentsTransformer {
    ArgumentsTransformer IDENTITY = (args, context) -> args;

    List<? extends Value> transform(@NotNull List<? extends Value> args, @NotNull EvaluationContextImpl context)
      throws EvaluateException, ClassNotLoadedException, InvalidTypeException;
  }
}
