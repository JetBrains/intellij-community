// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target;

import com.intellij.execution.target.HostPort;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

final class SimpleProxy {

  private static final Logger LOG = Logger.getInstance(SimpleProxy.class);
  private static final AtomicInteger ourClientId = new AtomicInteger();

  private final String myListenHostName;
  private final HostPort myRemoteHostPort;
  private ServerSocket myServerSocket;

  SimpleProxy(@NotNull String listenHostName, @NotNull HostPort remoteHostPort) throws IOException {
    myListenHostName = listenHostName;
    myRemoteHostPort = remoteHostPort;
    start();
  }

  private void start() throws IOException {
    ServerSocket serverSocket = new ServerSocket(0, 0, InetAddress.getByName(myListenHostName));
    myServerSocket = serverSocket;
    LOG.info("Proxy is listening on " + getListenAddr() + " -> " + getRemoteAddr());
    executeOnPooledThread(() -> {
      while (!serverSocket.isClosed()) {
        try {
          Socket client;
          try {
            client = serverSocket.accept();
          }
          catch (IOException e) {
            if (!myServerSocket.isClosed()) {
              throw e;
            }
            return;
          }
          executeOnPooledThread(() -> {
            processClientAndClose(client);
          });
        }
        catch (IOException e) {
          LOG.info("Cannot accept socket", e);
        }
      }
    });
  }

  private @NotNull String getListenAddr() {
    return myListenHostName + ":" + myServerSocket.getLocalPort();
  }

  private @NotNull String getRemoteAddr() {
    return myRemoteHostPort.getHost() + ":" + myRemoteHostPort.getPort();
  }

  private void processClientAndClose(@NotNull Socket client) {
    String prefix = "#" + ourClientId.incrementAndGet() + ": ";
    LOG.info(prefix + "client connected from " + client.getInetAddress() + ":" + client.getPort());
    InputStream clientInputStream;
    OutputStream clientOutputStream;
    try {
      clientInputStream = client.getInputStream();
      clientOutputStream = client.getOutputStream();
    }
    catch (IOException e) {
      LOG.info(prefix + "cannot open streams", e);
      return;
    }

    Socket remote;
    try {
      remote = new Socket(myRemoteHostPort.getHost(), myRemoteHostPort.getPort());
    }
    catch (IOException e) {
      LOG.info(prefix + "cannot connect to " + myRemoteHostPort, e);
      close(client);
      return;
    }

    InputStream remoteInputStream;
    OutputStream remoteOutputStream;
    try {
      remoteInputStream = remote.getInputStream();
      remoteOutputStream = remote.getOutputStream();
    }
    catch (IOException e) {
      LOG.info(prefix + "cannot open streams", e);
      return;
    }

    CompletableFuture<?> clientToRemote = copyStreamAsync(clientInputStream, remoteOutputStream, prefix + "client -> remote: ");
    CompletableFuture<?> remoteToClient = copyStreamAsync(remoteInputStream, clientOutputStream, prefix + "remote -> client: ");
    clientToRemote.whenComplete((o1, throwable1) -> {
      remoteToClient.whenComplete((o2, throwable2) -> {
        close(clientInputStream);
        close(remoteInputStream);
        close(client);
      });
    });
  }

  public void stop() {
    if (!myServerSocket.isClosed()) {
      close(myServerSocket);
    }
  }

  public int getListenPort() {
    return myServerSocket.getLocalPort();
  }

  private static void close(@NotNull Closeable closeable) {
    try {
      closeable.close();
    }
    catch (IOException e) {
      LOG.info("Cannot close", e);
    }
  }

  private static @NotNull CompletableFuture<?> copyStreamAsync(@NotNull InputStream source, @NotNull OutputStream dest,
                                                               @NotNull String description) {
    return executeOnPooledThread(() -> {
      LOG.debug(description + "start copying");
      try {
        source.transferTo(dest);
      }
      catch (IOException ignored) {
      }
      finally {
        LOG.debug(description + "done");
        close(dest);
      }
    });
  }

  private static @NotNull CompletableFuture<?> executeOnPooledThread(@NotNull Runnable r) {
    return CompletableFuture.supplyAsync(() -> {
      r.run();
      return null;
    }, AppExecutorUtil.getAppExecutorService());
  }

  /*
  public static void main(String[] args) throws IOException {
    try {
      SimpleProxy proxy = new SimpleProxy("172.29.128.1", new HostPort("127.0.0.1", 3010));
      System.out.println("http://" + proxy.getListenAddr());
      System.out.println("http://" + proxy.getRemoteAddr());
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
  */
}
