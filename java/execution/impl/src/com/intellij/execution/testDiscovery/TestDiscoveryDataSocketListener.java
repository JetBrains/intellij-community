// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.rt.coverage.data.api.TestDiscoveryProtocolUtil;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class TestDiscoveryDataSocketListener {
  private static final Logger LOG = Logger.getInstance(TestDiscoveryDataSocketListener.class);

  @Nullable
  private final String myModuleName;
  private final byte myFrameworkId;
  private volatile boolean myClosed;
  private volatile boolean myFinished;
  private final ServerSocket myServer;
  private final int myPort;
  private final TestDiscoveryIndex myTestDiscoveryIndex;

  public TestDiscoveryDataSocketListener(@NotNull Project project,
                                         @Nullable String moduleName,
                                         byte frameworkPrefix) throws IOException {
    myTestDiscoveryIndex = TestDiscoveryIndex.getInstance(project);
    myModuleName = moduleName;
    myFrameworkId = frameworkPrefix;
    myServer = new ServerSocket(0);
    myPort = myServer.getLocalPort();

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      if (myClosed) {
        return;
      }
      Socket socket = null;
      try {
        socket = myServer.accept();
        socket.setReceiveBufferSize(1024 * 1024);
        socket.setTcpNoDelay(true);
        InputStream testDataStream = socket.getInputStream();
        IdeaTestDiscoveryProtocolReader protocolReader = new IdeaTestDiscoveryProtocolReader(myTestDiscoveryIndex, myModuleName,
                                                                                             myFrameworkId);
        TestDiscoveryProtocolUtil.readSequentially(testDataStream, protocolReader);
        myFinished = true;
      }
      catch (IOException e) {
        if (!myClosed) {
          LOG.error(e);
        }
      } finally {
        try {
          if (socket != null) {
            socket.close();
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    });
  }

  public int getPort() {
    return myPort;
  }

  void attach(ProcessHandler handler) {
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        myClosed = true;
      }

      @Override
      public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
        myClosed = true;
      }
    });
  }

  @TestOnly
  public void awaitTermination() {
    while (!myFinished) {
      TimeoutUtil.sleep(100);
    }
  }
}
