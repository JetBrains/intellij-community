// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.ReferringObjectsProvider;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.memory.agent.parsers.GcRootsPathsParser;
import com.intellij.debugger.memory.agent.parsers.LongValueParser;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class MemoryAgentImpl implements MemoryAgent {
  public static final String PROXY_CLASS_NAME = "com.intellij.memory.agent.proxy.IdeaNativeAgentProxy";

  private static final Logger LOG = Logger.getInstance(MemoryAgentImpl.class);

  private static final String SIZE_OF_SINGLE_OBJECT_METHOD_NAME = "size";
  private static final String SIZE_OF_OBJECTS_METHOD_NAME = "size";
  private static final String GARBAGE_COLLECTOR_ROOTS_METHOD_NAME = "gcRoots";

  private final EvaluationContextImpl myEvaluationContext;
  private final ClassType myProxyClassType;

  public MemoryAgentImpl(@NotNull EvaluationContextImpl context, @NotNull ClassType reference) {
    myEvaluationContext = context;
    myProxyClassType = reference;
  }

  @Override
  public boolean canEvaluateObjectSize() {
    return !myProxyClassType.methodsByName(SIZE_OF_SINGLE_OBJECT_METHOD_NAME).isEmpty();
  }

  @Override
  public long evaluateObjectSize(@NotNull ObjectReference reference) {
    if (!canEvaluateObjectSize()) throw new UnsupportedOperationException();
    Value result = callMethod(SIZE_OF_SINGLE_OBJECT_METHOD_NAME, Collections.singletonList(reference));
    return result != null ? new LongValueParser().parse(result) : -1;
  }

  @Override
  public boolean canEvaluateObjectsSizes() {
    return !myProxyClassType.methodsByName(SIZE_OF_OBJECTS_METHOD_NAME).isEmpty();
  }

  @Override
  public List<Long> evaluateObjectsSizes(@NotNull List<ObjectReference> references) {
    if (!canEvaluateObjectsSizes()) throw new UnsupportedOperationException();
    Value result = callMethod(SIZE_OF_OBJECTS_METHOD_NAME, references);
    // TODO: Implement method and conversion
    return Collections.emptyList();
  }

  @Override
  public boolean canFindGcRoots() {
    return !myProxyClassType.methodsByName(GARBAGE_COLLECTOR_ROOTS_METHOD_NAME).isEmpty();
  }

  @Nullable
  @Override
  public ReferringObjectsProvider findGcRoots(@NotNull ObjectReference reference) {
    if (!canFindGcRoots()) throw new UnsupportedOperationException();
    Value value = callMethod(GARBAGE_COLLECTOR_ROOTS_METHOD_NAME, Collections.singletonList(reference));
    return value == null ? null : new GcRootsPathsParser().parse(value);
  }


  @Nullable
  private Value callMethod(@NotNull String methodName,
                           @NotNull List<? extends Value> args) {
    List<Method> methods = myProxyClassType.methodsByName(methodName);
    if (methods.isEmpty()) {
      LOG.error("Method \"" + methodName + "\" not found");
      return null;
    }
    if (methods.size() > 1) {
      LOG.warn("Too many methods \"" + methodName + "\" found. Count: " + methods.size());
    }

    Method method = methods.get(0);
    if (!method.isStatic()) {
      LOG.error("Utility method should be static");
    }
    try {
      return myEvaluationContext.getDebugProcess().invokeMethod(myEvaluationContext, myProxyClassType, method, args);
    }
    catch (EvaluateException e) {
      LOG.error("Something went wrong", e);
    }

    return null;
  }
}
