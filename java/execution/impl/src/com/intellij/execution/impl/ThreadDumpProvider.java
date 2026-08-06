// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.debugger.impl.attach.JavaDebuggerAttachUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.platform.eel.fs.EelFileUtils;
import com.intellij.platform.eel.fs.EelFiles;
import com.intellij.util.ArrayUtil;
import com.intellij.util.system.OS;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class ThreadDumpProvider {
  static final Logger LOG = Logger.getInstance(ThreadDumpProvider.class);
  private static final ExtensionPointName<ThreadDumpProvider> EP_NAME = new ExtensionPointName<>("com.intellij.threadDumpProvider");

  /**
   * Provides a thread dump containing platform threads only.
   *
   * @param pid local process id of the target JVM
   * @return first non-null dump from registered providers, or {@code null} if no provider produced one
   */
  public static @Nullable String dumpThreads(String pid) {
    return EP_NAME.computeSafeIfAny(p -> p.isAvailable(pid) ? p.dumpPlatformThreads(pid) : null);
  }

  /**
   * Provides a thread dump that includes both platform and virtual threads.
   *
   * <p>This operation may be slow. Providers should observe cancellation through {@code indicator} when it is supplied.
   *
   * @param pid       local process id of the target JVM
   * @param indicator progress and cancellation indicator for the dump command, or {@code null}
   * @return first non-null dump result from registered providers, or {@code null} if no provider produced one
   */
  public static @Nullable FullThreadDump dumpAllThreads(String pid, @Nullable ProgressIndicator indicator) {
    return EP_NAME.computeSafeIfAny(
      p -> p.isAvailable(pid) && p.canDumpVirtualThreads(pid) ? p.dumpThreadsWithVirtualThreads(pid, indicator) : null
    );
  }

  /**
   * Returns whether any registered provider can provide a thread that includes virtual threads.
   *
   * @param pid local process id of the target JVM
   */
  public static boolean isVirtualThreadsDumpAvailable(String pid) {
    return EP_NAME.computeSafeIfAny(p -> p.isAvailable(pid) && p.canDumpVirtualThreads(pid) ? Boolean.TRUE : null) != null;
  }

  protected abstract boolean isAvailable(String pid);

  protected abstract @Nullable String dumpPlatformThreads(String pid);

  protected boolean canDumpVirtualThreads(String pid) { return false; }

  protected @Nullable FullThreadDump dumpThreadsWithVirtualThreads(String pid, @Nullable ProgressIndicator indicator) { return null; }

  /**
   * Finds a JDK utility executable located next to the executable that started the target process.
   *
   * @param pid         local process id of the target JVM
   * @param utilityName utility base name, without an OS-specific executable suffix
   * @return absolute path to the executable utility, or {@code null} if the process does not exist, its executable is unknown,
   * or the sibling utility is not executable
   */
  protected static @Nullable Path findUtility(String pid, String utilityName) {
    ProcessHandle handle = ProcessHandle.of(Long.parseLong(pid)).orElse(null);
    if (handle == null) return null;
    String javaPath = handle.info().command().orElse(null);
    if (javaPath == null) return null;
    Path utilityPath = Path.of(javaPath).resolveSibling(OS.CURRENT.getBinaryName(utilityName)).toAbsolutePath();
    return Files.isExecutable(utilityPath) ? utilityPath : null;
  }

  @ApiStatus.Internal
  public sealed interface FullThreadDump {
    record Text(@NotNull String text) implements FullThreadDump {
    }

    record SavedToFile(@NotNull Path file) implements FullThreadDump {
    }
  }
}

class JstackThreadDumpProvider extends ThreadDumpProvider {
  @Override
  protected @Nullable String dumpPlatformThreads(String pid) {
    Path jstackPath = findUtility(pid, "jstack");
    if (jstackPath == null) return null;

    try {
      GeneralCommandLine command = new GeneralCommandLine(jstackPath.toString(), pid);
      ProcessOutput output = ExecUtil.execAndGetOutput(command);
      if (output.getExitCode() == 0) {
        return output.getStdout();
      }
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
    return null;
  }

  @Override
  protected boolean isAvailable(String pid) {
    return findUtility(pid, "jstack") != null;
  }
}

class JcmdThreadDumpProvider extends ThreadDumpProvider {
  private static final int JCMD_HELP_TIMEOUT_MS = 500;
  // Maximum size of a file with thread dump, which will be parsed and shown in ThreadDumpPanel
  private static final long MAX_FULL_DUMP_TEXT_SIZE_BYTES = 100L * 1024 * 1024;

  @Override
  protected @Nullable String dumpPlatformThreads(String pid) {
    return dumpPlatformThreadText(pid);
  }

  @Override
  protected boolean canDumpVirtualThreads(String pid) {
    return isJcmdDumpToFileWithJsonAvailable(pid);
  }

  @Override
  protected @Nullable FullThreadDump dumpThreadsWithVirtualThreads(String pid, @Nullable ProgressIndicator indicator) {
    return jcmdDumpToFile(pid, indicator);
  }

  private static @Nullable String dumpPlatformThreadText(String pid) {
    Path jcmdPath = findUtility(pid, "jcmd");
    if (jcmdPath == null) return null;
    try {
      GeneralCommandLine commandLine = new GeneralCommandLine(jcmdPath.toString(), pid, "Thread.print", "-l");
      ProcessOutput output = ExecUtil.execAndGetOutput(commandLine);
      return output.getExitCode() == 0 ? output.getStdout() : null;
    }
    catch (ExecutionException e) {
      LOG.warn(e);
      return null;
    }
  }

  private static @Nullable FullThreadDump jcmdDumpToFile(String pid, @Nullable ProgressIndicator indicator) {
    Path jcmdPath = findUtility(pid, "jcmd");
    if (jcmdPath == null) return null;

    Path dumpDir = null;
    boolean deleteDumpDir = true;
    try {
      dumpDir = Files.createTempDirectory("idea-thread-dump-");
      Path dumpFile = dumpDir.resolve("threads-" + pid + ".json");
      GeneralCommandLine commandLine =
        new GeneralCommandLine(jcmdPath.toString(), pid,
                               "Thread.dump_to_file", "-format=json", dumpFile.toAbsolutePath().toString());
      ProcessOutput output = execWithProgressIndicatorAndGetOutput(commandLine, indicator);
      if (output.isCancelled() || indicator != null && indicator.isCanceled()) {
        return null;
      }
      if (output.getExitCode() != 0) {
        LOG.debug("jcmd Thread.dump_to_file failed: " + output.getStderr());
        return null;
      }
      if (!Files.isRegularFile(dumpFile)) {
        LOG.debug("jcmd Thread.dump_to_file did not create " + dumpFile);
        return null;
      }
      FullThreadDump dump = readFullDumpFile(dumpFile);
      if (indicator != null && indicator.isCanceled()) {
        return null;
      }
      deleteDumpDir = dump instanceof FullThreadDump.Text;
      return dump;
    }
    catch (ExecutionException | IOException e) {
      LOG.warn(e);
      return null;
    }
    finally {
      if (dumpDir != null && deleteDumpDir) {
        try {
          EelFileUtils.deleteRecursively(dumpDir);
        }
        catch (IOException ignored) {
        }
      }
    }
  }

  private static @NotNull FullThreadDump readFullDumpFile(@NotNull Path dumpFile) throws IOException {
    if (isFullDumpTooLarge(Files.size(dumpFile))) {
      return new FullThreadDump.SavedToFile(dumpFile);
    }
    return new FullThreadDump.Text(EelFiles.readString(dumpFile, StandardCharsets.UTF_8));
  }

  private static boolean isFullDumpTooLarge(long fileSize) {
    return fileSize > MAX_FULL_DUMP_TEXT_SIZE_BYTES;
  }

  private static @NotNull ProcessOutput execWithProgressIndicatorAndGetOutput(GeneralCommandLine commandLine, @Nullable ProgressIndicator indicator) throws ExecutionException {
    CapturingProcessHandler processHandler = new CapturingProcessHandler(commandLine);
    if (indicator == null) {
      return processHandler.runProcess();
    }
    return processHandler.runProcessWithProgressIndicator(indicator);
  }

  @Override
  protected boolean isAvailable(String pid) {
    return findUtility(pid, "jcmd") != null;
  }

  private static boolean isJcmdDumpToFileWithJsonAvailable(String pid) {
    Path jcmdPath = findUtility(pid, "jcmd");
    if (jcmdPath == null) return false;

    try {
      GeneralCommandLine commandLine = new GeneralCommandLine(jcmdPath.toString(), pid, "help", "Thread.dump_to_file");
      ProcessOutput output = ExecUtil.execAndGetOutput(commandLine, JCMD_HELP_TIMEOUT_MS);
      if (output.getExitCode() != 0 || output.isTimeout()) return false;

      String helpText = output.getStdout() + '\n' + output.getStderr();
      return helpText.contains("-format") && helpText.contains("json");
    }
    catch (ExecutionException e) {
      LOG.warn(e);
      return false;
    }
  }
}

class AttachAPIThreadDumpProvider extends ThreadDumpProvider {
  @Override
  protected @Nullable String dumpPlatformThreads(String pid) {
    VirtualMachine vm = null;
    try {
      vm = JavaDebuggerAttachUtil.attachVirtualMachine(pid);
      InputStream inputStream = (InputStream)vm.getClass()
        .getMethod("remoteDataDump", Object[].class)
        .invoke(vm, new Object[]{ArrayUtil.EMPTY_OBJECT_ARRAY});
      try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
        return StreamUtil.readText(reader);
      }
    }
    catch (AttachNotSupportedException ignored) {
    }
    catch (Exception e) {
      LOG.warn(e);
    }
    finally {
      if (vm != null) {
        try {
          vm.detach();
        }
        catch (IOException ignored) {
        }
      }
    }
    return null;
  }

  @Override
  protected boolean isAvailable(String pid) {
    return true;
  }
}
