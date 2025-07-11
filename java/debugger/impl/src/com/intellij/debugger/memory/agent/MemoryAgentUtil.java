// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.memory.agent.extractor.AgentExtractor;
import com.intellij.debugger.memory.ui.JavaReferenceInfo;
import com.intellij.debugger.memory.ui.SizedReferenceInfo;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.system.CpuArch;
import com.intellij.util.system.OS;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class MemoryAgentUtil {
  private static final Logger LOG = Logger.getInstance(MemoryAgentUtil.class);
  private static final String MEMORY_AGENT_EXTRACT_DIRECTORY = "memory.agent.extract.dir";

  static @Nullable String getAgentFilePathAsString(boolean isInDebugMode, @NotNull AgentExtractor.AgentLibraryType libraryType)
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
    if (OS.CURRENT == OS.Linux &&arch == CpuArch.X86_64) return AgentExtractor.AgentLibraryType.LINUX_X64;
    if (OS.CURRENT == OS.Linux && arch == CpuArch.ARM64) return AgentExtractor.AgentLibraryType.LINUX_AARCH64;
    if (OS.CURRENT == OS.macOS) return AgentExtractor.AgentLibraryType.MACOS;
    if (OS.CURRENT == OS.Windows && arch == CpuArch.ARM64) return AgentExtractor.AgentLibraryType.WINDOWS_ARM64;
    if (OS.CURRENT == OS.Windows && arch == CpuArch.X86_64) return AgentExtractor.AgentLibraryType.WINDOWS64;
    if (OS.CURRENT == OS.Windows && arch == CpuArch.X86) return AgentExtractor.AgentLibraryType.WINDOWS32;
    throw new IllegalStateException("Unsupported OS and arch: " + OS.CURRENT + " " + arch);
  }

  public static @NotNull List<JavaReferenceInfo> calculateSizes(@NotNull EvaluationContextImpl context,
                                                                @NotNull ReferenceType classType,
                                                                long objectsLimit,
                                                                @NotNull ProgressIndicator progressIndicator) {
    MemoryAgent agent = MemoryAgent.get(context);
    agent.setProgressIndicator(progressIndicator);
    try {
      MemoryAgent.ObjectsAndSizes objectsAndSizes = agent.getSortedShallowAndRetainedSizesByClass(
        context,
        classType,
        objectsLimit,
        Registry.get("debugger.memory.agent.action.timeout").asInteger()
      ).getResult();
      return IntStreamEx.range(0, objectsAndSizes.getObjects().length)
        .mapToObj(i ->
          (JavaReferenceInfo) new SizedReferenceInfo(
            objectsAndSizes.getObjects()[i],
            objectsAndSizes.getShallowSizes()[i],
            objectsAndSizes.getRetainedSizes()[i]
          )
        )
        .toList();
    }
    catch (EvaluateException e) {
      LOG.error("Could not estimate objects sizes", e);
    }

    return Collections.emptyList();
  }

  public static List<JavaReferenceInfo> calculateSizesByObjects(@NotNull EvaluationContextImpl context,
                                                                @NotNull List<ObjectReference> references,
                                                                @NotNull ProgressIndicator progressIndicator) {
    MemoryAgent agent = MemoryAgent.get(context);
    agent.setProgressIndicator(progressIndicator);
    try {
      Pair<long[], long[]> sizes = agent.getShallowAndRetainedSizesByObjects(
        context,
        references,
        Registry.get("debugger.memory.agent.action.timeout").asInteger()
      ).getResult();
      return IntStreamEx.range(0, references.size())
        .mapToObj(i -> new SizedReferenceInfo(references.get(i), sizes.first[i], sizes.second[i]))
        .sortedBy(ref -> -ref.getRetainedSize())
        .map(ref -> (JavaReferenceInfo)ref)
        .toList();
    }
    catch (EvaluateException e) {
      LOG.error("Could not estimate objects sizes", e);
    }

    return Collections.emptyList();
  }

  public static boolean isPlatformSupported() {
    return SystemInfo.isWindows && (CpuArch.isIntel32() || CpuArch.isIntel64() || CpuArch.isArm64()) ||
           SystemInfo.isMac && (CpuArch.isIntel64() || CpuArch.isArm64()) ||
           SystemInfo.isLinux && (CpuArch.isIntel64() || CpuArch.isArm64());
  }

  private static @NotNull Path getAgentFile(boolean isInDebugMode, @NotNull AgentExtractor.AgentLibraryType libraryType)
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
