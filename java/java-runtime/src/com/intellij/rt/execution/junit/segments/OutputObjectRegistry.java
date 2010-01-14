/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.rt.execution.junit.segments;

import java.util.Hashtable;

public abstract class OutputObjectRegistry  {
  private final Hashtable myKnownKeys = new Hashtable();
  private int myLastIndex = 0;
  private PacketProcessor myMainTransport;
  private PacketProcessor myAuxilaryTransport;

  public OutputObjectRegistry(PacketProcessor transport) {
    myMainTransport = transport;
  }

  public OutputObjectRegistry(PacketProcessor mainTransport, PacketProcessor auxilaryTransport) {
    this(mainTransport);
    myAuxilaryTransport = auxilaryTransport;
  }

  public String referenceTo(Object test) {
    if (myKnownKeys.containsKey(test))
      return (String) myKnownKeys.get(test);
    return sendObject(test);
  }

  public Packet createPacket() {
    return new Packet(myMainTransport, this);
  }

  private String sendObject(Object test) {
    String key = String.valueOf(myLastIndex++);
    myKnownKeys.put(test, key);
    Packet packet = createPacket().addString(PoolOfDelimiters.OBJECT_PREFIX).addReference(key);
    addStringRepresentation(test, packet);
    packet.addLong(getTestCont(test));
    sendViaAllTransports(packet);
    return key;
  }

  protected abstract int getTestCont(Object test);
  protected abstract void addStringRepresentation(Object test, Packet packet);

  private void sendViaAllTransports(Packet packet) {
    packet.send();
    if (myAuxilaryTransport != null)
      packet.sendThrough(myAuxilaryTransport);
  }



  protected static void addTestClass(Packet packet, String className) {
    packet.
        addLimitedString(PoolOfTestTypes.TEST_CLASS).
        addLimitedString(className);
  }

  protected void addUnknownTest(Packet packet, Object test) {
    packet.
        addLimitedString(PoolOfTestTypes.UNKNOWN).
        addLong(getTestCont(test)).
        addLimitedString(test.getClass().getName());
  }

  protected static void addAllInPackage(Packet packet, String name) {
    packet.
        addLimitedString(PoolOfTestTypes.ALL_IN_PACKAGE).
        addLimitedString(name);
  }

  protected static void addTestMethod(Packet packet, String methodName, String className) {
    packet.
        addLimitedString(PoolOfTestTypes.TEST_METHOD).
        addLimitedString(methodName).
        addLimitedString(className);
  }

  public void forget(Object test) {
    myKnownKeys.remove(test);
  }
}
