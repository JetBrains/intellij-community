// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore.windows;

import com.intellij.util.system.WindowsSystemLibraries;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * DPAPI for the Password Safe: {@code CryptProtectData} and {@code CryptUnprotectData} downcalls into {@code crypt32.dll}.
 * Windows only: the first call loads the DLLs. The native copy of the input is zeroed after each call, and the output
 * buffer the API allocates goes back through {@code LocalFree}.
 */
@ApiStatus.Internal
public final class WindowsCryptUtils {
  private WindowsCryptUtils() { }

  /**
   * Protect the specified byte range
   *
   * @param data the data to protect
   * @return the protected form of the data
   */
  public static byte @NotNull [] protect(byte @NotNull [] data) {
    if (data.length == 0) return data;
    try (Arena arena = Arena.ofConfined()) {
      return call(Handles.CRYPT_PROTECT_DATA, "CryptProtectData", arena, data, arena.allocateFrom("Master Key", StandardCharsets.UTF_16LE));
    }
  }

  /**
   * Unprotect the specified byte range
   *
   * @param data the data to protect
   * @return the unprotected form of the data
   */
  public static byte @NotNull [] unprotect(byte @NotNull [] data) {
    if (data.length == 0) return data;
    try (Arena arena = Arena.ofConfined()) {
      return call(Handles.CRYPT_UNPROTECT_DATA, "CryptUnprotectData", arena, data, MemorySegment.NULL);
    }
  }

  /** Both functions share the shape {@code BOOL f(DATA_BLOB *in, LPWSTR description, DATA_BLOB *entropy, PVOID, PROMPTSTRUCT *, DWORD flags, DATA_BLOB *out)}. */
  private static byte[] call(MethodHandle function, String functionName, Arena arena, byte[] data, MemorySegment description) {
    MemorySegment input = arena.allocateFrom(JAVA_BYTE, data);
    try {
      MemorySegment in = arena.allocate(Handles.DATA_BLOB);
      in.set(JAVA_INT, 0, data.length);
      in.set(ADDRESS, 8, input);
      MemorySegment out = arena.allocate(Handles.DATA_BLOB);
      MemorySegment callState = arena.allocate(Handles.CALL_STATE_LAYOUT);
      int succeeded = (int)function.invokeExact(callState, in, description, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL, 0, out);
      if (succeeded == 0) {
        throw new RuntimeException(functionName + " failed: " + (int)Handles.LAST_ERROR.get(callState, 0L));
      }
      MemorySegment outData = out.get(ADDRESS, 8);
      try {
        return outData.reinterpret(out.get(JAVA_INT, 0)).toArray(JAVA_BYTE);
      }
      finally {
        MemorySegment ignored = (MemorySegment)Handles.LOCAL_FREE.invokeExact(outData);
      }
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
    finally {
      input.fill((byte)0);
    }
  }

  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup CRYPT32 = WindowsSystemLibraries.lookup("crypt32.dll");
    private static final SymbolLookup KERNEL32 = WindowsSystemLibraries.lookup("kernel32.dll");
    private static final Linker.Option CAPTURE_LAST_ERROR = Linker.Option.captureCallState("GetLastError");

    /** {@code DATA_BLOB { DWORD cbData; BYTE *pbData; }}, 16 bytes with padding */
    static final StructLayout DATA_BLOB = MemoryLayout.structLayout(JAVA_INT.withName("cbData"), MemoryLayout.paddingLayout(4), ADDRESS.withName("pbData"));
    static final StructLayout CALL_STATE_LAYOUT = Linker.Option.captureStateLayout();
    static final VarHandle LAST_ERROR = CALL_STATE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));

    private static final FunctionDescriptor CRYPT_DESCRIPTOR = FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS);

    /** {@code BOOL CryptProtectData(DATA_BLOB *in, LPCWSTR description, DATA_BLOB *entropy, PVOID reserved, CRYPTPROTECT_PROMPTSTRUCT *, DWORD flags, DATA_BLOB *out)} */
    static final MethodHandle CRYPT_PROTECT_DATA = LINKER.downcallHandle(CRYPT32.findOrThrow("CryptProtectData"), CRYPT_DESCRIPTOR, CAPTURE_LAST_ERROR);
    /** {@code BOOL CryptUnprotectData(DATA_BLOB *in, LPWSTR *description, DATA_BLOB *entropy, PVOID reserved, CRYPTPROTECT_PROMPTSTRUCT *, DWORD flags, DATA_BLOB *out)} */
    static final MethodHandle CRYPT_UNPROTECT_DATA = LINKER.downcallHandle(CRYPT32.findOrThrow("CryptUnprotectData"), CRYPT_DESCRIPTOR, CAPTURE_LAST_ERROR);
    /** {@code HLOCAL LocalFree(HLOCAL)} */
    static final MethodHandle LOCAL_FREE = LINKER.downcallHandle(KERNEL32.findOrThrow("LocalFree"), FunctionDescriptor.of(ADDRESS, ADDRESS));
  }
}
