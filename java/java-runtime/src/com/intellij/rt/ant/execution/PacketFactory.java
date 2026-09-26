// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.ant.execution;

class PacketFactory {
  private int myLastMessageId = -1;
  public static final PacketFactory ourInstance = new PacketFactory();

  public synchronized PacketWriter createPacket(char id) {
    PacketWriter writer = new PacketWriter();
    myLastMessageId++;
    writer.appendLong(myLastMessageId);
    writer.appendChar(id);
    return writer;
  }
}
