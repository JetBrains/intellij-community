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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class SegmentedOutputStream extends OutputStream implements PacketProcessor {
  private final PrintStream myPrintStream;
  private boolean myStarted = false;

  public SegmentedOutputStream(PrintStream transportStream) {
    this(transportStream, false);
  }

  public SegmentedOutputStream(PrintStream transportStream, boolean started) {
    myPrintStream = transportStream;
    myStarted = started;
    try {
      flush();
    }
    catch (IOException e) {
      throw new RuntimeException(e.getLocalizedMessage());
    }
  }

  public synchronized void write(int b) throws IOException {
    if (b == SegmentedStream.SPECIAL_SYMBOL && myStarted) writeNext(b);
    writeNext(b);
    flush();
  }

  public synchronized void flush() throws IOException {
    myPrintStream.flush();
  }

  public synchronized void close() throws IOException {
    myPrintStream.close();
  }

  private void writeNext(int b) {
    myPrintStream.write(b);
  }

  public synchronized void processPacket(String packet) {
    if (!myStarted)
      sendStart();
    writeNext(SegmentedStream.MARKER_PREFIX);
    String encodedPacket = Packet.encode(packet);
    writeNext(String.valueOf(encodedPacket.length())+SegmentedStream.LENGTH_DELIMITER+encodedPacket);
  }

  private void writeNext(String string) {
    try {
      myPrintStream.write(string.getBytes());
    } catch (IOException e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  public void sendStart() {
    writeNext(SegmentedStream.STARTUP_MESSAGE);
    myStarted = true;
  }

  public PrintStream getPrintStream() {
    return myPrintStream;
  }
}
