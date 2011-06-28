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

import junit.runner.BaseTestRunner;

import java.io.*;
import java.util.Collection;
import java.util.Vector;

public class Packet extends PacketWriter {
  private final OutputObjectRegistry myRegistry;
  private final PacketProcessor myTransport;
  public static final char ourSpecialSymbol = '$';
  public static final char[] ourSymbolsToEncode = new char[] {'\n', '\r', SegmentedStream.SPECIAL_SYMBOL};
  public static final int CODE_LENGTH = 2;

  public Packet(PacketProcessor transport, OutputObjectRegistry registry) {
    myTransport = transport;
    myRegistry = registry;
  }

  public Packet addObject(Object test) {
    return addReference(myRegistry.referenceTo(test));
  }

  public Packet addObject(Object test, Collection packet) {
    return addReference(myRegistry.referenceTo(test, packet));
  }

  public Packet addReference(String reference) {
    appendString(reference + PoolOfDelimiters.REFERENCE_END);
    return this;
  }

  public Packet switchInputTo(Object test) {
    appendString(PoolOfDelimiters.INPUT_COSUMER);
    return addObject(test);
  }

  public Packet addString(String string) {
    appendString(string);
    return this;
  }

  public void send() {
    sendThrough(myTransport);
  }

  public Packet addLong(long integer) {
    appendLong(integer);
    return this;
  }

  public Packet setTestState(Object test, int state) {
    return addString(PoolOfDelimiters.CHANGE_STATE).addObject(test).addLong(state);
  }

  public Packet addLimitedString(String message) {
    appendLimitedString(message);
    return this;
  }

  public Packet addThrowable(Throwable throwable) {
    String filteredTrace;
    try {
      filteredTrace = BaseTestRunner.getFilteredTrace(throwable);
    }
    catch (Throwable e) {
      filteredTrace = BaseTestRunner.getFilteredTrace(e);
    }

    String message;
    try {
      message = BaseTestRunner.getPreference("filterstack").equals("true") ? makeNewLinesCompatibleWithJUnit(throwableToString(throwable)) : throwableToString(throwable);
    }
    catch (Throwable e) {
      message = throwableToString(e);
    }

    addLimitedString(message);
    if (filteredTrace.startsWith(message))
      filteredTrace = filteredTrace.substring(message.length());
    addLimitedString(new TraceFilter(filteredTrace).execute());
    return this;
  }

  private static String throwableToString(final Throwable throwable) {
    final String tostring = throwable.toString();
    return tostring == null ? throwable.getClass().getName() : tostring;
  }

  private static String makeNewLinesCompatibleWithJUnit(String string) {
    try {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      BufferedReader reader = new BufferedReader(new StringReader(string));
      String line;
      while ((line = reader.readLine()) != null)
        writer.println(line);
      return buffer.getBuffer().toString();
    } catch (IOException e) {return null;}
  }

  public static String encode(String packet) {
    StringBuffer buffer = new StringBuffer(packet.length());
    for (int i = 0; i < packet.length(); i++) {
      char chr = packet.charAt(i);
      if (chr == ourSpecialSymbol) {
        buffer.append(chr);
        buffer.append(chr);
        continue;
      }
      boolean appendChar = true;
      for (int j = 0; j < ourSymbolsToEncode.length; j++) {
        if (ourSymbolsToEncode[j] == chr) {
          buffer.append(ourSpecialSymbol);
          final String code = String.valueOf((int)chr);
          for (int count = CODE_LENGTH - code.length(); count > 0; count--) {
            buffer.append("0");
          }
          buffer.append(code);
          appendChar = false;
          break;
        }
      }
      if (appendChar) {
        buffer.append(chr);
      }
    }
    return buffer.toString();
  }

  public Packet addStrings(Vector vector) {
    int size = vector.size();
    addLong(size);
    for (int i = 0; i < size; i++) {
      addLimitedString((String)vector.elementAt(i));
    }
    return this;
  }
}
