// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Based on the original work of Chris Johnsen: https://github.com/ChrisJohnsen/tmux-MacOSX-pasteboard
// Copyright (c) 2011-2013, Chris Johnsen <chris_johnsen@pobox.com>.
// Available under the terms of the BSD 2-Clause License (https://github.com/ChrisJohnsen/tmux-MacOSX-pasteboard/blob/12b77138a3/LICENSE).

package com.intellij.execution.process.mediator.daemon.util;

import com.intellij.execution.process.mediator.daemon.util.NativeCall.NativeCallException;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static com.intellij.execution.process.mediator.daemon.util.NativeCall.tryRun;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

public final class MachUtil {
  private MachUtil() {}

  public static boolean isMac() {
    return System.getProperty("os.name").startsWith("Mac");
  }

  public static void setup(@Nullable Integer machNamespaceUid) {
    if (!isMac()) {
      throw new IllegalStateException("macOS only");
    }

    if (machNamespaceUid != null) {
      tryRun(() -> machMoveToUserNamespace(machNamespaceUid), "Failed to move to namespace of UID " + machNamespaceUid);
    }
  }

  /**
   * Replaces the bootstrap port of this task with the bootstrap port of the user {@code uid}.
   * Every {@code mach_port_t} and {@code kern_return_t} is a 32-bit integer.
   */
  private static void machMoveToUserNamespace(int uid) throws NativeCallException {
    MemorySegment bootstrapPort = LibSystem.BOOTSTRAP_PORT;
    try (Arena arena = Arena.ofConfined()) {
      int bootstrap = bootstrapPort.get(JAVA_INT, 0);

      MemorySegment rootPort = arena.allocate(JAVA_INT);
      if (bootstrapGetRoot(bootstrap, rootPort) != LibSystem.KERN_SUCCESS) {
        throw new NativeCallException("bootstrap_get_root");
      }

      MemorySegment userPort = arena.allocate(JAVA_INT);
      if (bootstrapLookUpPerUser(bootstrap, uid, userPort) != LibSystem.KERN_SUCCESS) {
        throw new NativeCallException("bootstrap_look_up_per_user");
      }

      int task = machTaskSelf();
      if (taskSetBootstrapPort(task, userPort.get(JAVA_INT, 0)) != LibSystem.KERN_SUCCESS) {
        throw new NativeCallException("task_set_bootstrap_port");
      }

      if (machPortDeallocate(task, bootstrap) != LibSystem.KERN_SUCCESS) {
        throw new NativeCallException("mach_port_deallocate");
      }

      bootstrapPort.set(JAVA_INT, 0, userPort.get(JAVA_INT, 0));
    }
  }

  private static int bootstrapGetRoot(int bootstrap, MemorySegment rootPort) {
    try {
      return (int)LibSystem.BOOTSTRAP_GET_ROOT.invokeExact(bootstrap, rootPort);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static int bootstrapLookUpPerUser(int bootstrap, int uid, MemorySegment userPort) {
    try {
      return (int)LibSystem.BOOTSTRAP_LOOK_UP_PER_USER.invokeExact(bootstrap, MemorySegment.NULL, uid, userPort);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static int machTaskSelf() {
    try {
      return (int)LibSystem.MACH_TASK_SELF.invokeExact();
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** {@code task_set_special_port(task, TASK_BOOTSTRAP_PORT, port)}. */
  private static int taskSetBootstrapPort(int task, int port) {
    try {
      return (int)LibSystem.TASK_SET_SPECIAL_PORT.invokeExact(task, LibSystem.TASK_BOOTSTRAP_PORT, port);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static int machPortDeallocate(int task, int port) {
    try {
      return (int)LibSystem.MACH_PORT_DEALLOCATE.invokeExact(task, port);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** Downcalls into {@code libSystem}, the default lookup on macOS. A lookup failure surfaces as a {@link LinkageError}. */
  private static final class LibSystem {
    static final int KERN_SUCCESS = 0;
    static final int TASK_BOOTSTRAP_PORT = 4;

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = LINKER.defaultLookup();

    /** The global variable {@code mach_port_t bootstrap_port}. */
    static final MemorySegment BOOTSTRAP_PORT = LOOKUP.findOrThrow("bootstrap_port").reinterpret(JAVA_INT.byteSize());

    static final MethodHandle MACH_TASK_SELF = downcall("mach_task_self", FunctionDescriptor.of(JAVA_INT));
    static final MethodHandle MACH_PORT_DEALLOCATE = downcall("mach_port_deallocate", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT));
    static final MethodHandle TASK_SET_SPECIAL_PORT = downcall("task_set_special_port", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
    static final MethodHandle BOOTSTRAP_GET_ROOT = downcall("bootstrap_get_root", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS));
    static final MethodHandle BOOTSTRAP_LOOK_UP_PER_USER =
      downcall("bootstrap_look_up_per_user", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));

    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
      return LINKER.downcallHandle(LOOKUP.findOrThrow(name), descriptor);
    }
  }
}
