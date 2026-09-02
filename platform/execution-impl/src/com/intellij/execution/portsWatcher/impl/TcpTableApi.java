// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.portsWatcher.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * The listening TCP sockets of the local Windows host, read with a
 * <a href="https://learn.microsoft.com/en-us/windows/win32/api/iphlpapi/nf-iphlpapi-getextendedtcptable">GetExtendedTcpTable</a>
 * downcall on {@code iphlpapi.dll}. Windows only: the first call loads the DLL. The layouts are public for the layout test.
 */
@ApiStatus.Internal
public final class TcpTableApi {
  private TcpTableApi() { }

  /** {@code MIB_TCPROW_OWNER_PID} from {@code tcpmib.h}: six {@code DWORD}s, 24 bytes. */
  public static final StructLayout MIB_TCPROW_OWNER_PID = MemoryLayout.structLayout(
    JAVA_INT.withName("dwState"),
    JAVA_INT.withName("dwLocalAddr"),
    JAVA_INT.withName("dwLocalPort"),
    JAVA_INT.withName("dwRemoteAddr"),
    JAVA_INT.withName("dwRemotePort"),
    JAVA_INT.withName("dwOwningPid")
  );
  /** {@code MIB_TCPTABLE_OWNER_PID} starts with {@code DWORD dwNumEntries}; the rows follow it. */
  public static final long TABLE_OFFSET = JAVA_INT.byteSize();
  private static final long LOCAL_PORT_OFFSET = MIB_TCPROW_OWNER_PID.byteOffset(MemoryLayout.PathElement.groupElement("dwLocalPort"));
  private static final long OWNING_PID_OFFSET = MIB_TCPROW_OWNER_PID.byteOffset(MemoryLayout.PathElement.groupElement("dwOwningPid"));

  private static final int NO_ERROR = 0;
  public static final int ERROR_INVALID_PARAMETER = 87;
  private static final int ERROR_INSUFFICIENT_BUFFER = 122;

  private static final int AF_INET = 2;
  private static final int TCP_TABLE_OWNER_PID_LISTENER = 3;

  /**
   * One listening socket.
   *
   * @param localPort the {@code dwLocalPort} field as the API returns it: the port in network byte order in the low 16 bits
   * @param owningPid the {@code dwOwningPid} field, an unsigned {@code DWORD}
   */
  public record ListeningRow(int localPort, long owningPid) { }

  /** {@code GetExtendedTcpTable} failed. {@link #errorCode} is the value it returned. */
  public static final class Win32Exception extends IOException {
    public final int errorCode;

    Win32Exception(int errorCode) {
      super("GetExtendedTcpTable failed with Win32 error " + errorCode);
      this.errorCode = errorCode;
    }
  }

  /**
   * @return every listening IPv4 TCP socket with its owner, in table order
   */
  public static @NotNull List<ListeningRow> listeningRows() throws Win32Exception {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment size = arena.allocate(JAVA_INT);
      // The first call sizes the buffer. The table can grow between two calls, so retry while the buffer stays too small.
      MemorySegment table = MemorySegment.NULL;
      int status = getExtendedTcpTable(table, size);
      while (status == ERROR_INSUFFICIENT_BUFFER) {
        table = arena.allocate(Integer.toUnsignedLong(size.get(JAVA_INT, 0)));
        status = getExtendedTcpTable(table, size);
      }
      if (status != NO_ERROR) {
        throw new Win32Exception(status);
      }
      int count = table.get(JAVA_INT, 0);
      List<ListeningRow> rows = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        long row = TABLE_OFFSET + i * MIB_TCPROW_OWNER_PID.byteSize();
        rows.add(new ListeningRow(table.get(JAVA_INT, row + LOCAL_PORT_OFFSET), Integer.toUnsignedLong(table.get(JAVA_INT, row + OWNING_PID_OFFSET))));
      }
      return rows;
    }
  }

  private static int getExtendedTcpTable(MemorySegment table, MemorySegment size) {
    try {
      return (int)Handles.GET_EXTENDED_TCP_TABLE.invokeExact(table, size, 0, AF_INET, TCP_TABLE_OWNER_PID_LISTENER, 0);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** Downcalls into {@code iphlpapi.dll}, looked up by its absolute path. {@code DWORD}, {@code BOOL} and {@code ULONG} are {@code int}. */
  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup IPHLPAPI = SymbolLookup.libraryLookup(systemRoot().resolve("System32").resolve("iphlpapi.dll"), Arena.global());

    /** {@code DWORD GetExtendedTcpTable(PVOID table, PDWORD size, BOOL order, ULONG af, TCP_TABLE_CLASS class, ULONG reserved)} */
    static final MethodHandle GET_EXTENDED_TCP_TABLE = LINKER.downcallHandle(
      IPHLPAPI.findOrThrow("GetExtendedTcpTable"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));

    private static @NotNull Path systemRoot() {
      String systemRoot = System.getenv("SystemRoot");
      return Path.of(systemRoot != null ? systemRoot : "C:\\Windows");
    }
  }
}
