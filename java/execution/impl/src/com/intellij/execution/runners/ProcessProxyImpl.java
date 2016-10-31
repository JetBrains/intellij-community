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
package com.intellij.execution.runners;

import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.UnixProcessManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.TimeUnit;

/**
 * @author ven
 */
class ProcessProxyImpl implements ProcessProxy {
  static final Key<ProcessProxyImpl> KEY = Key.create("ProcessProxyImpl");

  private final AsynchronousChannelGroup myGroup;
  private final int myPort;

  private final Object myLock = new Object();
  private AsynchronousSocketChannel myConnection;
  private int myPid;

  ProcessProxyImpl(String mainClass) throws IOException {
    myGroup = AsynchronousChannelGroup.withFixedThreadPool(1, r -> new Thread(r, "Process Proxy: " + mainClass));
    AsynchronousServerSocketChannel channel = AsynchronousServerSocketChannel.open(myGroup)
      .bind(new InetSocketAddress("127.0.0.1", 0))
      .setOption(StandardSocketOptions.SO_REUSEADDR, true);
    myPort = ((InetSocketAddress)channel.getLocalAddress()).getPort();

    channel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
      @Override
      public void completed(AsynchronousSocketChannel channel, Void attachment) {
        synchronized (myLock) {
          myConnection = channel;
        }
      }

      @Override
      public void failed(Throwable t, Void attachment) { }
    });
  }

  int getPortNumber() {
    return myPort;
  }

  @Override
  public void attach(@NotNull ProcessHandler processHandler) {
    processHandler.putUserData(KEY, this);
    execute(() -> {
      int pid = -1;
      if (SystemInfo.isUnix && processHandler instanceof BaseOSProcessHandler) {
        pid = UnixProcessManager.getProcessPid(((BaseOSProcessHandler)processHandler).getProcess());
      }
      synchronized (myLock) {
        myPid = pid;
      }
    });
  }

  private void writeLine(String s) {
    execute(() -> {
      ByteBuffer out = ByteBuffer.wrap((s + '\n').getBytes("US-ASCII"));
      synchronized (myLock) {
        myConnection.write(out);
      }
    });
  }

  @Override
  public boolean canSendBreak() {
    if (SystemInfo.isWindows) {
      synchronized (myLock) {
        if (myConnection == null) return false;
      }
      return new File(PathManager.getBinPath(), "breakgen.dll").exists();
    }

    if (SystemInfo.isUnix) {
      synchronized (myLock) {
        return myPid > 0;
      }
    }

    return false;
  }

  @Override
  public boolean canSendStop() {
    synchronized (myLock) {
      return myConnection != null;
    }
  }

  @Override
  public void sendBreak() {
    if (SystemInfo.isWindows) {
      writeLine("BREAK");
    }
    else if (SystemInfo.isUnix) {
      int pid;
      synchronized (myLock) {
        pid = myPid;
      }
      UnixProcessManager.sendSignal(pid, 3);  // SIGQUIT
    }
  }

  @Override
  public void sendStop() {
    writeLine("STOP");
  }

  @Override
  public void destroy() {
    execute(() -> {
      synchronized (myLock) {
        if (myConnection != null) {
          myConnection.close();
        }
      }
    });
    execute(() -> {
      myGroup.shutdownNow();
      myGroup.awaitTermination(1, TimeUnit.SECONDS);
    });
  }

  private static void execute(ThrowableRunnable<Exception> block) {
    try {
      block.run();
    }
    catch (Exception e) {
      Logger.getInstance(ProcessProxy.class).warn(e);
    }
  }
}