// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.ReferringObjectsProvider;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.debugger.impl.ClassLoadingUtils;
import com.intellij.debugger.memory.agent.extractor.ProxyExtractor;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.ClassType;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class AgentLoader {
  public static final MemoryAgent DEFAULT_PROXY = new MyDisabledMemoryAgent();
  private static final Logger LOG = Logger.getInstance(AgentLoader.class);

  @NotNull
  public MemoryAgent load(@NotNull EvaluationContextImpl evaluationContext, @NotNull VirtualMachineProxy virtualMachine) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      ClassType classType = ensureClassLoaded(evaluationContext, virtualMachine);
      if (classType != null) {
        MemoryAgentImpl agent = new MemoryAgentImpl(evaluationContext.getDebugProcess(), classType);
        agent.initializeCapabilities();
        return agent.isLoaded() ? agent : DEFAULT_PROXY;
      }
    }
    catch (EvaluateException e) {
      LOG.info("Could not load proxy class", e);
    }

    return DEFAULT_PROXY;
  }

  @Nullable
  private static ClassType ensureClassLoaded(@NotNull EvaluationContextImpl context, @NotNull VirtualMachineProxy vm)
    throws EvaluateException {
    List<ReferenceType> classes = vm.classesByName(MemoryAgentImpl.PROXY_CLASS_NAME);
    if (classes.isEmpty()) {
      ClassType classType = loadUtilityClass(context);
      if (classType == null) {
        LOG.error("Could not load proxy class");
      }
      return classType;
    }

    LOG.assertTrue(classes.size() == 1, "Too many utility classes loaded: " + classes.size());
    return (ClassType)classes.get(0);
  }

  @Nullable
  private static ClassType loadUtilityClass(@NotNull EvaluationContextImpl context) throws EvaluateException {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    byte[] bytes = readUtilityClass();
    ClassLoaderReference classLoader = ClassLoadingUtils.getClassLoader(context, debugProcess);
    ClassLoadingUtils.defineClass(MemoryAgentImpl.PROXY_CLASS_NAME, bytes, context, debugProcess, classLoader);
    return (ClassType)debugProcess.findClass(context, MemoryAgentImpl.PROXY_CLASS_NAME, classLoader);
  }

  @NotNull
  private static byte[] readUtilityClass() {
    return new ProxyExtractor().extractProxy();
  }

  private static class MyDisabledMemoryAgent implements MemoryAgent {
    @Override
    public boolean isLoaded() {
      return false;
    }

    @Override
    public boolean canEvaluateObjectSize() {
      return false;
    }

    @Override
    public long evaluateObjectSize(@NotNull ObjectReference reference) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean canEvaluateObjectsSizes() {
      return false;
    }

    @Override
    public List<Long> evaluateObjectsSizes(@NotNull List<ObjectReference> references) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean canFindGcRoots() {
      return false;
    }

    @Override
    public ReferringObjectsProvider findGcRoots(@NotNull ObjectReference reference) {
      throw new UnsupportedOperationException();
    }
  }
}
