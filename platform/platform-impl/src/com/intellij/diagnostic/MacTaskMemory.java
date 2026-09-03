// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * The virtual memory statistics of this task through a {@code task_info(TASK_VM_INFO)} downcall. macOS only: the symbols are looked up on the first call.
 */
@ApiStatus.Internal
final class MacTaskMemory {
  private MacTaskMemory() { }

  /** {@code resident_size}, {@code internal}, {@code external} and {@code phys_footprint} of {@code task_vm_info} */
  record Info(long residentSize, long internal, long external, long physFootprint) { }

  private static final int TASK_VM_INFO = 22;
  /** {@code sizeof(task_vm_info_data_t)} in the macOS 14 SDK, 352 bytes; the kernel fills as much as it knows and updates the count */
  private static final int TASK_VM_INFO_SIZE = 352;
  private static final long RESIDENT_SIZE = 16;
  private static final long INTERNAL = 48;
  private static final long EXTERNAL = 64;
  private static final long PHYS_FOOTPRINT = 144;

  /** @return the statistics, or {@code null} when the call fails */
  static @Nullable Info read() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment info = arena.allocate(TASK_VM_INFO_SIZE);
      MemorySegment count = arena.allocate(JAVA_INT);
      count.set(JAVA_INT, 0, TASK_VM_INFO_SIZE / Integer.BYTES);
      int task = (int)Handles.MACH_TASK_SELF.invokeExact();
      int result = (int)Handles.TASK_INFO.invokeExact(task, TASK_VM_INFO, info, count);
      if (result != 0) {
        return null;
      }
      return new Info(info.get(JAVA_LONG, RESIDENT_SIZE), info.get(JAVA_LONG, INTERNAL), info.get(JAVA_LONG, EXTERNAL), info.get(JAVA_LONG, PHYS_FOOTPRINT));
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();

    /** {@code mach_port_t mach_task_self(void)} */
    static final MethodHandle MACH_TASK_SELF = LINKER.downcallHandle(LINKER.defaultLookup().findOrThrow("mach_task_self"), FunctionDescriptor.of(JAVA_INT));
    /** {@code kern_return_t task_info(task_name_t, task_flavor_t, task_info_t out, mach_msg_type_number_t *count)} */
    static final MethodHandle TASK_INFO = LINKER.downcallHandle(
      LINKER.defaultLookup().findOrThrow("task_info"), FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
  }
}
