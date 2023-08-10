// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

// Based on the original work of Chris Johnsen: https://github.com/ChrisJohnsen/tmux-MacOSX-pasteboard
// Copyright (c) 2011-2013, Chris Johnsen <chris_johnsen@pobox.com>.
// Available under the terms of the BSD 2-Clause License (https://github.com/ChrisJohnsen/tmux-MacOSX-pasteboard/blob/12b77138a3/LICENSE).

package com.intellij.execution.process.mediator.util;

import com.intellij.execution.process.mediator.util.NativeCall.NativeCallException;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import com.sun.jna.ptr.IntByReference;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Proxy;
import java.util.Objects;

import static com.intellij.execution.process.mediator.util.NativeCall.tryRun;

public final class MachUtil {
  private MachUtil() {}

  public static boolean isMac() {
    return Platform.isMac();
  }

  public static void setup(@Nullable Integer machNamespaceUid) {
    if (!isMac()) {
      throw new IllegalStateException("macOS only");
    }

    if (machNamespaceUid != null) {
      tryRun(() -> machMoveToUserNamespace(machNamespaceUid), "Failed to move to namespace of UID " + machNamespaceUid);
    }
  }

  private static void machMoveToUserNamespace(int uid) throws NativeCallException {
    LibSystem libSystem;
    try {
      libSystem = Native.load("System", LibSystem.class);
    }
    catch (UnsatisfiedLinkError e) {
      throw new IllegalStateException("Unable to load libSystem", e);
    }

    NativeLibrary systemLibrary = Objects.requireNonNull(((Library.Handler)Proxy.getInvocationHandler(libSystem)).getNativeLibrary());

    IntByReference bootstrapPort = new IntByReference();
    bootstrapPort.setPointer(systemLibrary.getGlobalVariableAddress("bootstrap_port"));

    IntByReference rootPort = new IntByReference();
    if (libSystem.bootstrap_get_root(bootstrapPort.getValue(), rootPort) != LibSystem.KERN_SUCCESS) {
      throw new NativeCallException("bootstrap_get_root");
    }

    IntByReference userPort = new IntByReference();
    if (libSystem.bootstrap_look_up_per_user(bootstrapPort.getValue(), null, uid, userPort) != LibSystem.KERN_SUCCESS) {
      throw new NativeCallException("bootstrap_look_up_per_user");
    }

    int task = libSystem.mach_task_self();
    if (libSystem.task_set_special_port(task, LibSystem.TASK_BOOTSTRAP_PORT, userPort.getValue()) != LibSystem.KERN_SUCCESS) {
      throw new NativeCallException("task_set_bootstrap_port");
    }

    if (libSystem.mach_port_deallocate(task, bootstrapPort.getValue()) != LibSystem.KERN_SUCCESS) {
      throw new NativeCallException("mach_port_deallocate");
    }

    bootstrapPort.setValue(userPort.getValue());
  }


  private interface LibSystem extends Library {
    int KERN_SUCCESS = 0;

    int TASK_BOOTSTRAP_PORT = 4;

    int mach_task_self();

    int mach_port_deallocate(int task, int name);

    int task_set_special_port(int task, int kind, int port);

    int bootstrap_get_root(int bp, IntByReference ret);

    int bootstrap_look_up_per_user(int bp, String name, int uid, IntByReference ret);
  }
}
