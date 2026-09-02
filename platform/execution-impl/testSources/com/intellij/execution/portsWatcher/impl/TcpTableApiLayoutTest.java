// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.portsWatcher.impl;

import org.junit.Test;

import java.lang.foreign.MemoryLayout;

import static org.junit.Assert.assertEquals;

/** Pins the ABI of {@code MIB_TCPTABLE_OWNER_PID} on every OS: the layout is plain data and touches no Windows library. */
public class TcpTableApiLayoutTest {
  @Test
  public void rowLayout() {
    assertEquals(24, TcpTableApi.MIB_TCPROW_OWNER_PID.byteSize());
    assertEquals(8, TcpTableApi.MIB_TCPROW_OWNER_PID.byteOffset(MemoryLayout.PathElement.groupElement("dwLocalPort")));
    assertEquals(20, TcpTableApi.MIB_TCPROW_OWNER_PID.byteOffset(MemoryLayout.PathElement.groupElement("dwOwningPid")));
    assertEquals(4, TcpTableApi.TABLE_OFFSET);
  }
}
