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

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

class TestDiscoveryDataSocketListener {
  private static final Logger LOG = Logger.getInstance(TestDiscoveryDataSocketListener.class);

  @Nullable
  private final String myModuleName;
  @NotNull
  private final String myFrameworkPrefix;
  private volatile boolean myClosed;
  private final ServerSocket myServer;
  private final int myPort;
  private final TestDiscoveryIndex myTestDiscoveryIndex;

  public TestDiscoveryDataSocketListener(@NotNull Project project,
                                         @Nullable String moduleName,
                                         @NotNull String frameworkPrefix) throws IOException {
    myTestDiscoveryIndex = TestDiscoveryIndex.getInstance(project);
    myModuleName = moduleName;
    myFrameworkPrefix = frameworkPrefix;
    myServer = new ServerSocket(0);
    myPort = myServer.getLocalPort();

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Socket socket;
      while (true) {
        if (myClosed) {
          return;
        }
        try {
          socket = myServer.accept();
          if (socket != null) {
            break;
          }
        }
        catch (IOException e) {
          LOG.error(e);
          return;
        }
        TimeoutUtil.sleep(10);
      }

      try {
        InputStream testDataStream = socket.getInputStream();
        IdeaTestDiscoveryProtocolReader protocolReader = new IdeaTestDiscoveryProtocolReader(myTestDiscoveryIndex, myModuleName, myFrameworkPrefix);
        TestDiscoveryProtocolUtil.readSequentially(testDataStream, protocolReader);
      }
      catch (IOException e) {
        if (!myClosed) {
          LOG.error(e);
        }
      } finally {
        try {
          socket.close();
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
}
