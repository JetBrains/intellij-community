// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.integrationTests.fakeServer;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * The server process that the IDE starts in the tests with the fake LSP server.
 * It copies its stdin to a socket of the test process and the socket to its stdout. The fake server itself runs in the test process,
 * so a test sets up its expectations as plain lambdas.
 * <p>
 * The process exits when either side closes. The class uses only the JDK, so the IDE runs it with its own Java and no classpath
 * besides the directory of this class.
 */
public final class FakeLspServerRelay {
  private FakeLspServerRelay() { }

  @SuppressWarnings("UnnecessaryModifier")
  public static void main(String[] args) throws IOException {
    try (Socket socket = new Socket(args[0], Integer.parseInt(args[1]))) {
      socket.setTcpNoDelay(true);
      Thread stdinToSocket = new Thread(() -> {
        copy(new FileInputStream(FileDescriptor.in), outputOf(socket));
        System.exit(0);
      }, "stdin to socket");
      stdinToSocket.setDaemon(true);
      stdinToSocket.start();
      copy(socket.getInputStream(), new FileOutputStream(FileDescriptor.out));
    }
    System.exit(0);
  }

  private static OutputStream outputOf(Socket socket) {
    try {
      return socket.getOutputStream();
    }
    catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void copy(InputStream from, OutputStream to) {
    try {
      byte[] buffer = new byte[8192];
      int count;
      while ((count = from.read(buffer)) >= 0) {
        to.write(buffer, 0, count);
        to.flush();
      }
    }
    catch (IOException ignored) {
      // the other side closed
    }
  }
}
