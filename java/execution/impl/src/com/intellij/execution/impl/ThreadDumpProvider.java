// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.debugger.impl.attach.JavaDebuggerAttachUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.ArrayUtil;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public abstract class ThreadDumpProvider {
  static final Logger LOG = Logger.getInstance(ThreadDumpProvider.class);
  private static final ExtensionPointName<ThreadDumpProvider> EP_NAME = new ExtensionPointName<>("com.intellij.threadDumpProvider");

  public static String dump(String pid) {
    return EP_NAME.computeSafeIfAny(p -> p.dumpThreads(pid));
  }

  protected abstract String dumpThreads(String pid);
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
}