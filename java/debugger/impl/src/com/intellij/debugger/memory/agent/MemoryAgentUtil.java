// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.memory.agent.extractor.AgentExtractor;
import com.intellij.debugger.memory.ui.JavaReferenceInfo;
import com.intellij.debugger.memory.ui.SizedReferenceInfo;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.system.CpuArch;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class MemoryAgentUtil {
  private static final Logger LOG = Logger.getInstance(MemoryAgentUtil.class);
  private static final String MEMORY_AGENT_EXTRACT_DIRECTORY = "memory.agent.extract.dir";

  private static final int ESTIMATE_OBJECTS_SIZE_LIMIT = 2000;

  @Nullable
  static String getAgentFilePathAsString(boolean isInDebugMode, @NotNull AgentExtractor.AgentLibraryType libraryType)
    throws ExecutionException, InterruptedException, TimeoutException {
    Path agentFile = getAgentFile(isInDebugMode, libraryType);
    return JavaExecutionUtil.handleSpacesInAgentPath(
      agentFile.toAbsolutePath().toString(),
      "debugger-memory-agent",
      MEMORY_AGENT_EXTRACT_DIRECTORY
    );
  }

  static AgentExtractor.@NotNull AgentLibraryType detectAgentKindByArch(CpuArch arch) {
    LOG.assertTrue(isPlatformSupported());
    if (SystemInfo.isLinux) return AgentExtractor.AgentLibraryType.LINUX;
    if (SystemInfo.isMac) return AgentExtractor.AgentLibraryType.MACOS;
    return arch.width == 32 ? AgentExtractor.AgentLibraryType.WINDOWS32 : AgentExtractor.AgentLibraryType.WINDOWS64;
  }

  @NotNull
  public static List<JavaReferenceInfo> tryCalculateSizes(@NotNull EvaluationContextImpl context,
                                                          @NotNull List<JavaReferenceInfo> objects) {
    MemoryAgent agent = MemoryAgent.get(context);
    if (!agent.getCapabilities().canEstimateObjectsSizes()) return objects;
    if (objects.size() > ESTIMATE_OBJECTS_SIZE_LIMIT) {
      LOG.info("Too many objects to estimate their sizes");
      return objects;
    }
    try {
      long[] sizes = agent.estimateObjectsSizes(
        context,
        ContainerUtil.map(objects, x -> x.getObjectReference()),
        Registry.get("debugger.memory.agent.action.timeout").asInteger()
      ).getResult();
      return IntStreamEx.range(0, objects.size())
        .mapToObj(i -> new SizedReferenceInfo(objects.get(i).getObjectReference(), sizes[i]))
        .reverseSorted(Comparator.comparing(x -> x.size()))
        .map(x -> (JavaReferenceInfo)x)
        .toList();
    }
    catch (EvaluateException e) {
      LOG.error("Could not estimate objects sizes", e);
    }

    return objects;
  }

  public static boolean isPlatformSupported() {
    return SystemInfo.isWindows && (CpuArch.isIntel32() || CpuArch.isIntel64()) ||
           SystemInfo.isMac && CpuArch.isIntel64() ||
           SystemInfo.isLinux && CpuArch.isIntel64();
  }

  @NotNull
  private static Path getAgentFile(boolean isInDebugMode, @NotNull AgentExtractor.AgentLibraryType libraryType)
    throws InterruptedException, ExecutionException, TimeoutException {
    if (isInDebugMode) {
      String debugAgentPath = Registry.get("debugger.memory.agent.debug.path").asString();
      if (!debugAgentPath.isEmpty()) {
        LOG.info("Local memory agent will be used: " + debugAgentPath);
        return Paths.get(debugAgentPath);
      }
    }

    return ApplicationManager.getApplication()
      .executeOnPooledThread(() -> AgentExtractor.INSTANCE.extract(libraryType, getAgentDirectory()))
      .get(1, TimeUnit.SECONDS);
  }

  private static @NotNull Path getAgentDirectory() {
    String agentDirectory = System.getProperty(MEMORY_AGENT_EXTRACT_DIRECTORY);
    if (agentDirectory != null) {
      File file = new File(agentDirectory);
      if (file.exists() || file.mkdirs()) {
        return file.toPath();
      }

      LOG.info("Directory specified in property \"" + MEMORY_AGENT_EXTRACT_DIRECTORY +
               "\" not found. Default tmp directory will be used");
    }

    return Paths.get(FileUtil.getTempDirectory());
  }
}
