// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runners;

import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.UnixProcessManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

final class ProcessProxyImpl implements ProcessProxy {
  static final Key<ProcessProxyImpl> KEY = Key.create("ProcessProxyImpl");
  private static final Path ourBreakgenHelper = SystemInfo.isWindows ? PathManager.findBinFile("breakgen.dll") : null;

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
        pid = OSProcessUtil.getProcessID(((BaseOSProcessHandler)processHandler).getProcess());
      }
      synchronized (myLock) {
        myPid = pid;
      }
    });
  }

  private void writeLine(String s) {
    execute(() -> {
      ByteBuffer out = ByteBuffer.wrap((s + '\n').getBytes(StandardCharsets.US_ASCII));
      synchronized (myLock) {
        myConnection.write(out);
      }
    });
  }

  String getBinPath() {
    if (SystemInfo.isWindows) {
      if (ourBreakgenHelper != null) {
        return ourBreakgenHelper.getParent().toString();
      }
    }
    return PathManager.getBinPath();
  }

  @Override
  public boolean canSendBreak() {
    if (SystemInfo.isWindows && (CpuArch.isArm64() || CpuArch.isIntel64() || CpuArch.isIntel32())) {  // see also: `AppMainV2#loadHelper`
      synchronized (myLock) {
        if (myConnection == null) return false;
      }
      return ourBreakgenHelper != null;
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
