// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl;

import com.intellij.util.system.WindowsSystemLibraries;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/** Drive roots through {@code kernel32.dll} downcalls. Windows only: the first call loads the DLL. */
@ApiStatus.Internal
public final class WindowsDrives {
  private WindowsDrives() { }

  /** {@code DRIVE_FIXED} */
  private static final int DRIVE_FIXED = 3;

  /**
   * @return the roots of the fixed drives, for example {@code C:\\}, from {@code GetLogicalDriveStringsW} filtered by {@code GetDriveTypeW}
   */
  public static @NotNull List<String> fixedDriveRoots() {
    try (Arena arena = Arena.ofConfined()) {
      int length = (int)Handles.GET_LOGICAL_DRIVE_STRINGS.invokeExact(0, MemorySegment.NULL);
      if (length <= 0) {
        return List.of();
      }
      MemorySegment buffer = arena.allocate(2L * (length + 1));
      int copied = (int)Handles.GET_LOGICAL_DRIVE_STRINGS.invokeExact(length + 1, buffer);
      if (copied <= 0) {
        return List.of();
      }
      // the buffer holds "C:\\", "D:\\", ... each ended by a NUL, then a final NUL
      String block = new String(buffer.asSlice(0, 2L * copied).toArray(JAVA_BYTE), StandardCharsets.UTF_16LE);
      List<String> result = new ArrayList<>();
      int start = 0;
      while (start < block.length()) {
        int end = block.indexOf('\0', start);
        if (end < 0) end = block.length();
        String root = block.substring(start, end);
        start = end + 1;
        if (root.isEmpty()) continue;
        int type = (int)Handles.GET_DRIVE_TYPE.invokeExact(arena.allocateFrom(root, StandardCharsets.UTF_16LE));
        if (type == DRIVE_FIXED) {
          result.add(root);
        }
      }
      return result;
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup KERNEL32 = WindowsSystemLibraries.lookup("kernel32.dll");

    /** {@code DWORD GetLogicalDriveStringsW(DWORD bufferLength, LPWSTR buffer)}: the length needed when the buffer is too small */
    static final MethodHandle GET_LOGICAL_DRIVE_STRINGS = LINKER.downcallHandle(KERNEL32.findOrThrow("GetLogicalDriveStringsW"), FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS));
    /** {@code UINT GetDriveTypeW(LPCWSTR root)} */
    static final MethodHandle GET_DRIVE_TYPE = LINKER.downcallHandle(KERNEL32.findOrThrow("GetDriveTypeW"), FunctionDescriptor.of(JAVA_INT, ADDRESS));
  }
}
