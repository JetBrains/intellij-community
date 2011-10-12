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
package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.junit.segments.OutputObjectRegistry;
import com.intellij.rt.execution.junit.segments.Packet;
import junit.framework.ComparisonFailure;

public class FileComparisonFailure extends ComparisonFailure implements KnownException {
  private final String myExpected;
  private final String myActual;
  private final String myFilePath;

  public FileComparisonFailure(String message, String expected, String actual, String filePath) {
    super(message, expected, actual);
    myExpected = expected;
    myActual = actual;
    myFilePath = filePath;
  }

  public PacketFactory getPacketFactory() {
    return new MyPacketFactory(this, myExpected, myActual, myFilePath);
  }

  private static class MyPacketFactory extends ComparisonDetailsExtractor {
    private final String myFilePath;

    public MyPacketFactory(ComparisonFailure assertion, String expected, String actual, String filePath) {
      super(assertion, expected, actual);
      myFilePath = filePath;
    }

    public Packet createPacket(OutputObjectRegistry registry, Object test) {
      Packet packet = super.createPacket(registry, test);
      packet.addLimitedString(myFilePath);
      return packet;
    }
  }
}
