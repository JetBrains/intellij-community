package com.intellij.rt.execution.junit.segments;

import java.io.OutputStream;
import java.io.IOException;

public class EchoOutputStream extends OutputStream {
  private final OutputStream myOut;
  private final OutputStream myEcho;

  public EchoOutputStream(OutputStream out, OutputStream echo) {
    myOut = out;
    myEcho = echo;
  }

  public synchronized void write(int b) throws IOException {
    myOut.write(b);
    myEcho.write(b);
  }
}
