// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

final class MemoryAgentInitializer {
  private static final Key<MemoryAgent> MEMORY_AGENT_KEY = Key.create("MEMORY_AGENT_KEY");
  private static final Logger LOG = Logger.getInstance(MemoryAgentInitializer.class);

  static @NotNull MemoryAgent getAgent(@NotNull EvaluationContextImpl evaluationContext) {
    MemoryAgent agent = evaluationContext.getDebugProcess().getUserData(MEMORY_AGENT_KEY);
    return agent == null ? initializeAgent(evaluationContext) : agent;
  }

  static boolean isAgentLoaded(@NotNull DebugProcess debugProcess) {
    MemoryAgent memoryAgent = debugProcess.getUserData(MEMORY_AGENT_KEY);
    return memoryAgent != null && !memoryAgent.isDisabled();
  }

  static MemoryAgent initializeAgent(@NotNull EvaluationContextImpl evaluationContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    MemoryAgent agent = MemoryAgentImpl.DISABLED;

    try {
      agent = MemoryAgentImpl.createMemoryAgent(evaluationContext);
    }
    catch (EvaluateException e) {
      LOG.error("Could not initialize memory agent. ", e);
    }
    evaluationContext.getDebugProcess().putUserData(MEMORY_AGENT_KEY, agent);
    return agent;
  }
}
