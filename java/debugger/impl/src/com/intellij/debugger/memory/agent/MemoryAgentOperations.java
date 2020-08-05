// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.ClassLoadingUtils;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.memory.agent.extractor.ProxyExtractor;
import com.intellij.debugger.memory.agent.parsers.BooleanParser;
import com.intellij.debugger.memory.agent.parsers.GcRootsPathsParser;
import com.intellij.debugger.memory.agent.parsers.LongArrayParser;
import com.intellij.debugger.memory.agent.parsers.LongValueParser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class MemoryAgentOperations {
  private static final Key<MemoryAgent> MEMORY_AGENT_KEY = Key.create("MEMORY_AGENT_KEY");
  private static final Logger LOG = Logger.getInstance(MemoryAgentOperations.class);

  static long estimateObjectSize(@NotNull EvaluationContextImpl evaluationContext, @NotNull ObjectReference reference)
    throws EvaluateException {
    Value result = callMethod(evaluationContext, MemoryAgentNames.Methods.ESTIMATE_OBJECT_SIZE, Collections.singletonList(reference));
    return LongValueParser.INSTANCE.parse(result);
  }

  static long @NotNull [] estimateObjectsSizes(@NotNull EvaluationContextImpl evaluationContext, @NotNull List<ObjectReference> references)
    throws EvaluateException {
    ArrayReference array = wrapWithArray(evaluationContext, references);
    Value result = callMethod(evaluationContext, MemoryAgentNames.Methods.ESTIMATE_OBJECTS_SIZE, Collections.singletonList(array));
    return LongArrayParser.INSTANCE.parse(result).stream().mapToLong(Long::longValue).toArray();
  }

  @NotNull
  static ReferringObjectsInfo findReferringObjects(@NotNull EvaluationContextImpl evaluationContext,
                                                   @NotNull ObjectReference reference, int limit) throws EvaluateException {
    IntegerValue limitValue = evaluationContext.getDebugProcess().getVirtualMachineProxy().mirrorOf(limit);
    Value value = callMethod(evaluationContext, MemoryAgentNames.Methods.FIND_GC_ROOTS, Arrays.asList(reference, limitValue));
    return GcRootsPathsParser.INSTANCE.parse(value);
  }

  @NotNull
  static MemoryAgent getAgent(@NotNull DebugProcessImpl debugProcess) {
    MemoryAgent agent = debugProcess.getUserData(MEMORY_AGENT_KEY);
    return agent == null ? MemoryAgentImpl.DISABLED : agent;
  }

  static void initializeAgent(@NotNull EvaluationContextImpl context) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    MemoryAgent agent = MemoryAgentImpl.DISABLED;
    try {
      agent = new MemoryAgentImpl(initializeCapabilities(context));
    }
    catch (EvaluateException e) {
      LOG.error("Could not initialize memory agent. ", e);
    }
    context.getDebugProcess().putUserData(MEMORY_AGENT_KEY, agent);
  }

  private static MemoryAgentCapabilities initializeCapabilities(@NotNull EvaluationContextImpl context) throws EvaluateException {
    ClassType proxyType = getProxyType(context);
    boolean isAgentLoaded = checkAgentCapability(context, proxyType, MemoryAgentNames.Methods.IS_LOADED);
    if (!isAgentLoaded) {
      return MemoryAgentCapabilities.DISABLED;
    }
    else {
      MemoryAgentCapabilities.Builder builder = new MemoryAgentCapabilities.Builder();
      return builder
        .setCanEstimateObjectSize(checkAgentCapability(context, proxyType, MemoryAgentNames.Methods.CAN_ESTIMATE_OBJECT_SIZE))
        .setCanEstimateObjectsSizes(checkAgentCapability(context, proxyType, MemoryAgentNames.Methods.CAN_ESTIMATE_OBJECTS_SIZES))
        .setCanFindGcRoots(checkAgentCapability(context, proxyType, MemoryAgentNames.Methods.CAN_FIND_GC_ROOTS))
        .buildLoaded();
    }
  }

  @NotNull
  private static ClassType getProxyType(@NotNull EvaluationContextImpl evaluationContext) throws EvaluateException {
    boolean valueBefore = evaluationContext.isAutoLoadClasses();
    try {
      return getOrLoadProxyType(evaluationContext);
    }
    finally {
      evaluationContext.setAutoLoadClasses(valueBefore);
    }
  }

  private static ClassType getOrLoadProxyType(@NotNull EvaluationContextImpl evaluationContext) throws EvaluateException {
    ClassObjectReference classObjectReference = evaluationContext.computeAndKeep(() -> {
      long start = System.currentTimeMillis();
      ReferenceType referenceType = loadUtilityClass(evaluationContext);
      if (referenceType == null) {
        throw EvaluateExceptionUtil.createEvaluateException("Could not load memory agent proxy class");
      }
      long duration = System.currentTimeMillis() - start;
      LOG.info("Loading of agent proxy class took " + duration + " ms");

      return referenceType.classObject();
    });

    return (ClassType)classObjectReference.reflectedType();
  }

  private static boolean checkAgentCapability(@NotNull EvaluationContextImpl evaluationContext,
                                              @NotNull ClassType proxyType,
                                              @NotNull String capabilityMethodName) {
    try {
      Value value = callMethod(evaluationContext, proxyType, capabilityMethodName, Collections.emptyList());
      return BooleanParser.INSTANCE.parse(value);
    }
    catch (EvaluateException e) {
      LOG.warn("Exception while capability checking: ", e);
    }
    return false;
  }

  private static Value callMethod(@NotNull EvaluationContextImpl evaluationContext,
                                  @NotNull String methodName,
                                  @NotNull List<? extends Value> args) throws EvaluateException {
    ClassType proxyType = getProxyType(evaluationContext);
    return callMethod(evaluationContext, proxyType, methodName, args);
  }

  @NotNull
  private static Value callMethod(@NotNull EvaluationContextImpl evaluationContext,
                                  @NotNull ClassType proxyType,
                                  @NotNull String methodName,
                                  @NotNull List<? extends Value> args) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    long start = System.currentTimeMillis();
    List<Method> methods = DebuggerUtilsEx.declaredMethodsByName(proxyType, methodName);
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


    Value result = evaluationContext
      .computeAndKeep(() -> evaluationContext.getDebugProcess().invokeMethod(evaluationContext, proxyType, method, args, true));

    LOG.info("Memory agent's method \"" + methodName + "\" took " + (System.currentTimeMillis() - start) + " ms");
    return result;
  }

  private static byte @NotNull [] readUtilityClass() {
    return new ProxyExtractor().extractProxy();
  }

  @Nullable
  private static ReferenceType loadUtilityClass(@NotNull EvaluationContextImpl context) throws EvaluateException {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    byte[] bytes = readUtilityClass();
    context.setAutoLoadClasses(true);
    ClassLoaderReference classLoader = ClassLoadingUtils.getClassLoader(context, debugProcess);
    ClassLoadingUtils.defineClass(MemoryAgentNames.PROXY_CLASS_NAME, bytes, context, debugProcess, classLoader);
    try {
      return debugProcess.loadClass(context, MemoryAgentNames.PROXY_CLASS_NAME, classLoader);
    }
    catch (InvocationException | ClassNotLoadedException | IncompatibleThreadStateException | InvalidTypeException e) {
      throw EvaluateExceptionUtil.createEvaluateException("Could not load proxy class", e);
    }
  }

  @NotNull
  private static ArrayReference wrapWithArray(@NotNull EvaluationContextImpl context, @NotNull List<ObjectReference> references)
    throws EvaluateException {
    long start = System.currentTimeMillis();
    ArrayType longArray = (ArrayType)context.getDebugProcess().findClass(context, "java.lang.Object[]", context.getClassLoader());
    ArrayReference instancesArray = longArray.newInstance(references.size());
    try {
      instancesArray.setValues(references);
    }
    catch (InvalidTypeException | ClassNotLoadedException e) {
      throw EvaluateExceptionUtil.createEvaluateException("Could not wrap objects with array", e);
    }
    LOG.info("Wrapping values with array took " + (System.currentTimeMillis() - start) + " ms");
    return instancesArray;
  }
}
