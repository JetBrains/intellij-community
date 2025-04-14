// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.debugger.impl.attach.JavaDebuggerAttachUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.ArrayUtil;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class ThreadDumpProvider {
  static final Logger LOG = Logger.getInstance(ThreadDumpProvider.class);
  private static final ExtensionPointName<ThreadDumpProvider> EP_NAME = new ExtensionPointName<>("com.intellij.threadDumpProvider");

  public static String dump(String pid) {
    return EP_NAME.computeSafeIfAny(p -> p.isAvailable() ? p.dumpThreads(pid) : null);
  }

  protected abstract boolean isAvailable();
  protected abstract String dumpThreads(String pid);
}

class JstackThreadDumpProvider extends ThreadDumpProvider {
  @Override
  protected String dumpThreads(String pid) {
    ProcessHandle handle = ProcessHandle.of(Long.parseLong(pid)).orElse(null);
    if (handle != null) {
      String commandLine = handle.info().command().orElse(null);
      if (commandLine != null) {
        Path javaPath = Paths.get(commandLine);
        String jstack = "jstack" + (SystemInfo.isWindows ? ".exe" : "");
        Path jstackPath = javaPath.resolveSibling(jstack);
        if (Files.isExecutable(jstackPath)) {
          try {
            GeneralCommandLine command = new GeneralCommandLine(jstackPath.toAbsolutePath().toString(), pid);
            ProcessOutput output = ExecUtil.execAndGetOutput(command);
            if (output.getExitCode() == 0) {
              return output.getStdout();
            }
          }
          catch (ExecutionException e) {
            LOG.error(e);
          }
        }
      }
    }
    return null;
  }

  @Override
  protected boolean isAvailable() {
    return true;
  }
}

class AttachAPIThreadDumpProvider extends ThreadDumpProvider {
  @Override
  protected String dumpThreads(String pid) {
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
  protected boolean isAvailable() {
    return true;
  }
}