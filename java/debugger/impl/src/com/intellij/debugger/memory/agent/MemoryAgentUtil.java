// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.DebugProcessAdapterImpl;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.memory.agent.extractor.AgentExtractor;
import com.intellij.debugger.memory.ui.JavaReferenceInfo;
import com.intellij.debugger.memory.ui.SizedReferenceInfo;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.jar.Attributes;

public class MemoryAgentUtil {
  private static final Logger LOG = Logger.getInstance(MemoryAgentUtil.class);
  private static final int ESTIMATE_OBJECTS_SIZE_LIMIT = 2000;

  public static void addMemoryAgent(@NotNull JavaParameters parameters) {
    if (!DebuggerSettings.getInstance().ENABLE_MEMORY_AGENT) {
      return;
    }

    if (isIbmJdk(parameters)) {
      LOG.info("Do not attach memory agent for IBM jdk");
      return;
    }

    ParametersList parametersList = parameters.getVMParametersList();
    if (parametersList.getParameters().stream().anyMatch(x -> x.contains("memory_agent"))) return;
    boolean isInDebugMode = Registry.is("debugger.memory.agent.debug");
    File agentFile = null;
    String errorMessage = null;
    long start = System.currentTimeMillis();
    try {
      agentFile = getAgentFile(isInDebugMode);
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
    if (errorMessage != null || agentFile == null) {
      LOG.warn("Could not extract agent: " + errorMessage);
      return;
    }

    LOG.info("Memory agent extracting took " + (System.currentTimeMillis() - start) + " ms");
    String path = JavaExecutionUtil.handleSpacesInAgentPath(agentFile.getAbsolutePath(), "debugger-memory-agent", null);
    String args = "";
    if (isInDebugMode) {
      args = "5";// Enable debug messages
    }
    path += "=" + args;
    parametersList.add("-agentpath:" + path);
  }

  public static List<JavaReferenceInfo> tryCalculateSizes(@NotNull List<JavaReferenceInfo> objects, @Nullable MemoryAgent agent) {
    if (agent == null || !agent.canEvaluateObjectsSizes()) return objects;
    if (objects.size() > ESTIMATE_OBJECTS_SIZE_LIMIT) {
      LOG.info("Too many objects to estimate their sizess");
      return objects;
    }
    try {
      long[] sizes = agent.evaluateObjectsSizes(ContainerUtil.map(objects, x -> x.getObjectReference()));
      return IntStreamEx.range(0, objects.size())
        .mapToObj(i -> new SizedReferenceInfo(objects.get(i).getObjectReference(), sizes[i]))
        .reverseSorted(Comparator.comparing(x -> x.size()))
        .map(x -> (JavaReferenceInfo)x)
        .toList();
    }
    catch (EvaluateException e) {
      LOG.error("Could not estimate objects sizes");
    }

    return objects;
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
        if (!DebuggerSettings.getInstance().ENABLE_MEMORY_AGENT) {
          LOG.info("Memory agent disabled");
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

  private static boolean isIbmJdk(@NotNull JavaParameters parameters) {
    Sdk jdk = parameters.getJdk();
    String vendor = jdk == null ? null : JdkUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VENDOR);
    return vendor != null && StringUtil.containsIgnoreCase(vendor, "ibm");
  }

  private static File getAgentFile(boolean isInDebugMode) throws InterruptedException, ExecutionException, TimeoutException {
    if (isInDebugMode) {
      String debugAgentPath = Registry.get("debugger.memory.agent.debug.path").asString();
      if (!debugAgentPath.isEmpty()) {
        return new File(debugAgentPath);
      }
    }

    return ApplicationManager.getApplication()
      .executeOnPooledThread(() -> new AgentExtractor().extract()).get(1, TimeUnit.SECONDS);
  }
}
