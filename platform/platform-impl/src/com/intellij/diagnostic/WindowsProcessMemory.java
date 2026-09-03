// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.util.system.WindowsSystemLibraries;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * The memory counters of this process through a {@code GetProcessMemoryInfo} downcall into {@code psapi.dll}.
 * Windows only: the first call loads the DLLs.
 */
@ApiStatus.Internal
final class WindowsProcessMemory {
  private WindowsProcessMemory() { }

  /** {@code WorkingSetSize}, {@code PrivateUsage} and {@code PrivateWorkingSetSize} of {@code PROCESS_MEMORY_COUNTERS_EX2} */
  record Counters(long workingSetSize, long privateUsage, long privateWorkingSetSize) { }

  /** @return the counters, or {@code null} when the call fails */
  static @Nullable Counters read() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment counters = arena.allocate(Handles.PROCESS_MEMORY_COUNTERS_EX2);
      counters.set(JAVA_INT, 0, (int)Handles.PROCESS_MEMORY_COUNTERS_EX2.byteSize());
      MemorySegment process = (MemorySegment)Handles.GET_CURRENT_PROCESS.invokeExact();
      int succeeded = (int)Handles.GET_PROCESS_MEMORY_INFO.invokeExact(process, counters, (int)Handles.PROCESS_MEMORY_COUNTERS_EX2.byteSize());
      if (succeeded == 0) {
        return null;
      }
      return new Counters(
        counters.get(JAVA_LONG, offset("WorkingSetSize")),
        counters.get(JAVA_LONG, offset("PrivateUsage")),
        counters.get(JAVA_LONG, offset("PrivateWorkingSetSize")));
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static long offset(String field) {
    return Handles.PROCESS_MEMORY_COUNTERS_EX2.byteOffset(MemoryLayout.PathElement.groupElement(field));
  }

  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();

    /**
     * {@code PROCESS_MEMORY_COUNTERS_EX2}, 96 bytes on x64 and ARM64: two {@code DWORD}s, then ten {@code SIZE_T}s and one {@code ULONG64}.
     * {@code PrivateWorkingSetSize} needs Windows 10 22H2 or Windows 11 22H2 with the September 2023 update; older systems leave it 0.
     */
    static final StructLayout PROCESS_MEMORY_COUNTERS_EX2 = MemoryLayout.structLayout(
      JAVA_INT.withName("cb"), JAVA_INT.withName("PageFaultCount"),
      JAVA_LONG.withName("PeakWorkingSetSize"), JAVA_LONG.withName("WorkingSetSize"),
      JAVA_LONG.withName("QuotaPeakPagedPoolUsage"), JAVA_LONG.withName("QuotaPagedPoolUsage"),
      JAVA_LONG.withName("QuotaPeakNonPagedPoolUsage"), JAVA_LONG.withName("QuotaNonPagedPoolUsage"),
      JAVA_LONG.withName("PagefileUsage"), JAVA_LONG.withName("PeakPagefileUsage"),
      JAVA_LONG.withName("PrivateUsage"), JAVA_LONG.withName("PrivateWorkingSetSize"), JAVA_LONG.withName("SharedCommitUsage"));

    /** {@code HANDLE GetCurrentProcess()} */
    static final MethodHandle GET_CURRENT_PROCESS = LINKER.downcallHandle(
      WindowsSystemLibraries.lookup("kernel32.dll").findOrThrow("GetCurrentProcess"), FunctionDescriptor.of(ADDRESS));
    /** {@code BOOL GetProcessMemoryInfo(HANDLE, PPROCESS_MEMORY_COUNTERS, DWORD cb)} */
    static final MethodHandle GET_PROCESS_MEMORY_INFO = LINKER.downcallHandle(
      WindowsSystemLibraries.lookup("psapi.dll").findOrThrow("GetProcessMemoryInfo"), FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
  }
}
