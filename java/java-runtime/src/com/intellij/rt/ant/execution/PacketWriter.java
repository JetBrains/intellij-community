/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.rt.ant.execution;

public class PacketWriter {
  private final StringBuffer myBody = new StringBuffer();

  public void appendString(String string) {
    myBody.append(string);
  }

  public void appendLong(long integer) {
    myBody.append(integer);
    myBody.append(PoolOfDelimiters.INTEGER_DELIMITER);
  }

  public void appendLimitedString(String message) {
    if (message == null)
      appendLimitedString("");
    else {
      appendLong(message.length());
      appendString(message);
    }
  }

  public String getString() {
    return myBody.toString();
  }

  public void sendThrough(PacketProcessor transport) {
    transport.processPacket(getString());
  }

  public void appendChar(char aChar) {
    myBody.append(aChar);
  }
}
