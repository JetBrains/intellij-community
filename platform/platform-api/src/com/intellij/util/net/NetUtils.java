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
package com.intellij.util.net;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.CountingGZIPInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;

public class NetUtils {
  private static final Logger LOG = Logger.getInstance(NetUtils.class);

  private NetUtils() { }

  public static boolean canConnectToSocket(String host, int port) {
    if (isLocalhost(host)) {
      return !canBindToLocalSocket(host, port);
    }
    else {
      return canConnectToRemoteSocket(host, port);
    }
  }

  @Deprecated
  public static InetAddress getLoopbackAddress() {
    return InetAddress.getLoopbackAddress();
  }

  public static boolean isLocalhost(@NotNull String host) {
    return host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1");
  }

  private static boolean canBindToLocalSocket(String host, int port) {
    try {
      ServerSocket socket = new ServerSocket();
      try {
        //it looks like this flag should be set but it leads to incorrect results for NodeJS under Windows
        //socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(host, port));
      }
      finally {
        try {
          socket.close();
        }
        catch (IOException ignored) {
        }
      }
      return true;
    }
    catch (IOException e) {
      LOG.debug(e);
      return false;
    }
  }

  public static boolean canConnectToRemoteSocket(String host, int port) {
    try {
      Socket socket = new Socket(host, port);
      socket.close();
      return true;
    }
    catch (IOException ignored) {
      return false;
    }
  }

  public static int findAvailableSocketPort() throws IOException {
    final ServerSocket serverSocket = new ServerSocket(0);
    try {
      int port = serverSocket.getLocalPort();
      // workaround for linux : calling close() immediately after opening socket
      // may result that socket is not closed
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (serverSocket) {
        try {
          //noinspection WaitNotInLoop
          serverSocket.wait(1);
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
      }
      return port;
    }
    finally {
      serverSocket.close();
    }
  }

  public static int tryToFindAvailableSocketPort(int defaultPort) {
    try {
      return findAvailableSocketPort();
    }
    catch (IOException ignored) {
      return defaultPort;
    }
  }

  public static int tryToFindAvailableSocketPort() {
    return tryToFindAvailableSocketPort(-1);
  }

  public static int[] findAvailableSocketPorts(int capacity) throws IOException {
    final int[] ports = new int[capacity];
    final ServerSocket[] sockets = new ServerSocket[capacity];

    for (int i = 0; i < capacity; i++) {
      //noinspection SocketOpenedButNotSafelyClosed
      final ServerSocket serverSocket = new ServerSocket(0);
      sockets[i] = serverSocket;
      ports[i] = serverSocket.getLocalPort();
    }
    //workaround for linux : calling close() immediately after opening socket
    //may result that socket is not closed
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (sockets) {
      try {
        //noinspection WaitNotInLoop
        sockets.wait(1);
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
    }

    for (ServerSocket socket : sockets) {
      socket.close();
    }
    return ports;
  }

  public static String getLocalHostString() {
    // HACK for Windows with ipv6
    String localHostString = "localhost";
    try {
      final InetAddress localHost = InetAddress.getByName(localHostString);
      if ((localHost.getAddress().length != 4 && SystemInfo.isWindows) ||
          (localHost.getAddress().length == 4 && SystemInfo.isMac)) {
        localHostString = "127.0.0.1";
      }
    }
    catch (UnknownHostException ignored) {
    }
    return localHostString;
  }

  /**
   * @param indicator             progress indicator
   * @param inputStream           source stream
   * @param outputStream          destination stream
   * @param expectedContentLength expected content length in bytes, used in progress indicator (negative means unknown length).
   *                              For gzipped content, it's an expected length of gzipped/compressed content.
   *                              E.g. for HTTP, it means how many bytes should be sent over the network.
   * @return the total number of bytes written to the destination stream (may exceed expectedContentLength for gzipped content)
   * @throws IOException              if IO error occur
   * @throws ProcessCanceledException if process was canceled.
   */
  public static int copyStreamContent(@Nullable ProgressIndicator indicator,
                                      @NotNull InputStream inputStream,
                                      @NotNull OutputStream outputStream,
                                      int expectedContentLength) throws IOException, ProcessCanceledException {
    if (indicator != null) {
      indicator.checkCanceled();
      if (expectedContentLength < 0) {
        indicator.setIndeterminate(true);
      }
    }
    CountingGZIPInputStream gzipStream = ObjectUtils.tryCast(inputStream, CountingGZIPInputStream.class);
    final byte[] buffer = new byte[8 * 1024];
    int count;
    int bytesWritten = 0;
    long bytesRead = 0;
    while ((count = inputStream.read(buffer)) > 0) {
      outputStream.write(buffer, 0, count);
      bytesWritten += count;
      bytesRead = gzipStream != null ? gzipStream.getCompressedBytesRead() : bytesWritten;

      if (indicator != null) {
        indicator.checkCanceled();
        if (expectedContentLength > 0) {
          indicator.setFraction((double)bytesRead / expectedContentLength);
        }
      }
    }

    if (indicator != null) {
      indicator.checkCanceled();
    }

    if (bytesRead < expectedContentLength) {
      throw new IOException(String.format("Connection closed at byte %d. Expected %d bytes.", bytesRead, expectedContentLength));
    }

    return bytesWritten;
  }

  public static boolean isSniEnabled() {
    return SystemProperties.getBooleanProperty("jsse.enableSNIExtension", true);
  }
}
