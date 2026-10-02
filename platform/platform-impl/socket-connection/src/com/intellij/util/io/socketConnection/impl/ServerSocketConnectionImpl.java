// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.socketConnection.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.socketConnection.AbstractRequest;
import com.intellij.util.io.socketConnection.AbstractResponse;
import com.intellij.util.io.socketConnection.ConnectionStatus;
import com.intellij.util.io.socketConnection.RequestResponseExternalizerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public final class ServerSocketConnectionImpl<Request extends AbstractRequest, Response extends AbstractResponse> extends SocketConnectionBase<Request,Response> {
  private static final Logger LOG = Logger.getInstance(ServerSocketConnectionImpl.class);
  private ServerSocket myServerSocket;
  private final int myDefaultPort;
  private final int myPortChoiceAttempts;
  private final @Nullable InetAddress myBindAddress;

  public ServerSocketConnectionImpl(int defaultPort,
                                    @Nullable InetAddress bindAddress,
                                    int portChoiceAttempts,
                                    @NotNull RequestResponseExternalizerFactory<Request, Response> factory) {
    super(factory);
    myDefaultPort = defaultPort;
    myPortChoiceAttempts = portChoiceAttempts;
    myBindAddress = bindAddress;
  }

  @Override
  public void open() throws IOException {
    myServerSocket = createSocket();
    setPort(myServerSocket.getLocalPort());
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        waitForConnection();
      }
      catch (IOException e) {
        LOG.info(e);
        setStatus(ConnectionStatus.CONNECTION_FAILED, "Connection failed: " + e.getMessage());
      }
    });
  }

  private @NotNull ServerSocket createSocket() throws IOException {
    IOException exc = null;
    for (int i = 0; i < myPortChoiceAttempts; i++) {
      int port = myDefaultPort + i;
      try {
        return new ServerSocket(port, 0, myBindAddress);
      }
      catch (IOException e) {
        exc = e;
        LOG.info(e);
      }
    }
    throw exc;
  }

  private void waitForConnection() throws IOException {
    addThreadToInterrupt();
    try {
      setStatus(ConnectionStatus.WAITING_FOR_CONNECTION, null);
      LOG.debug("waiting for connection on port " + getPort());

      try (Socket socket = myServerSocket.accept()) {
        attachToSocket(socket);
      }
    }
    finally {
      myServerSocket.close();
      removeThreadToInterrupt();
    }
  }
}
