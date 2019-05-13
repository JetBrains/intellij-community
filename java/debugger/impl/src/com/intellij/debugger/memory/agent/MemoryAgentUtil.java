// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.DebugProcessAdapterImpl;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.memory.agent.extractor.AgentExtractor;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class MemoryAgentUtil {
  private static final Logger LOG = Logger.getInstance(MemoryAgentUtil.class);

  public static void addMemoryAgent(JavaParameters parameters) {
    if (!Registry.is("debugger.enable.memory.agent")) {

      return;
    }

    ParametersList parametersList = parameters.getVMParametersList();
    if (parametersList.getParameters().stream().anyMatch(x -> x.contains("memory_agent"))) return;
    File extractedAgent = null;
    String errorMessage = null;
    long start = System.currentTimeMillis();
    try {
      extractedAgent = ApplicationManager.getApplication()
        .executeOnPooledThread(() -> new AgentExtractor().extract()).get(1, TimeUnit.SECONDS);
    }
    catch (InterruptedException e) {
      errorMessage = "Interrupted";
    }
    catch (ExecutionException e) {
      LOG.warn(e.getCause());
      errorMessage = "Exception thrown (see logs for details)";
    }
    catch (TimeoutException e) {
      errorMessage = "Timeout";
    }
    if (errorMessage != null || extractedAgent == null) {
      LOG.warn("Could not extract agent: " + errorMessage);
      return;
    }

    LOG.info("Memory agent extracting took " + (System.currentTimeMillis() - start) + " ms");
    String path = JavaExecutionUtil.handleSpacesInAgentPath(extractedAgent.getAbsolutePath(), "debugger-memory-agent", null);
    parametersList.add("-agentpath:" + path);
  }

  public static void loadAgentProxy(@NotNull DebugProcessImpl debugProcess, @NotNull Consumer<MemoryAgent> agentLoaded) {
    debugProcess.addDebugProcessListener(new DebugProcessAdapterImpl() {
      @Override
      public void paused(SuspendContextImpl suspendContext) {
        MemoryAgent memoryAgent = initMemoryAgent(suspendContext);
        if (memoryAgent == null) {
          LOG.warn("Could not initialize memory agent.");
          return;
        }

        agentLoaded.accept(memoryAgent);
        debugProcess.removeDebugProcessListener(this);
      }

      @Nullable
      private MemoryAgent initMemoryAgent(@NotNull SuspendContextImpl suspendContext) {
        if (!Registry.is("debugger.enable.memory.agent")) {
          LOG.info("Memory agent disabled by registry key");
          return AgentLoader.DEFAULT_PROXY;
        }

        StackFrameProxyImpl frameProxy = suspendContext.getFrameProxy();
        if (frameProxy == null) {
          LOG.warn("frame proxy is not available");
          return null;
        }

        long start = System.currentTimeMillis();
        EvaluationContextImpl evaluationContext = new EvaluationContextImpl(suspendContext, frameProxy);
        MemoryAgent agent = new AgentLoader().load(evaluationContext, debugProcess.getVirtualMachineProxy());
        LOG.info("Memory agent loading took " + (System.currentTimeMillis() - start) + " ms");
        return agent;
      }
    });
  }
}
