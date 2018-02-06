// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.rt.coverage.data.SocketTestDataReader;
import com.intellij.rt.coverage.data.SocketTestDiscoveryProtocolDataListener;
import com.intellij.rt.coverage.data.TestDiscoveryProtocolDataListener;
import com.intellij.util.TimeoutUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class TestDiscoveryDataSocketListener {
  private static final Logger LOG = Logger.getInstance(TestDiscoveryDataSocketListener.class);

  private final @Nullable String myModuleName;
  private final @NotNull String myFrameworkPrefix;
  private final ServerSocket myServer;
  private final int myPort;
  private final TestDiscoveryIndex myTestDiscoveryIndex;
  private final TIntObjectHashMap<String> myTestExecutionNameEnumerator = new TIntObjectHashMap<>();
  private volatile boolean myCloseForcibly;
  private volatile boolean myStarted;

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
      myStarted = true;
      while (true) {
        if (myCloseForcibly) {
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
        listenForFinishedTests(socket);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    });

    while (!myStarted) {
      TimeoutUtil.sleep(10);
    }
  }

  private void listenForFinishedTests(@NotNull Socket socket) throws IOException {
    InputStream testDataStream = socket.getInputStream();

    while (true) {
      byte msgType = (byte)testDataStream.read();
      switch (msgType) {
        case TestDiscoveryProtocolDataListener.START_MARKER:
          int version = testDataStream.read();
          LOG.assertTrue(version == SocketTestDiscoveryProtocolDataListener.VERSION);
          LOG.debug("test discovery started");
          break;
        case TestDiscoveryProtocolDataListener.FINISH_MARKER:
          LOG.debug("test discovery finished");
          socket.close();
          myServer.close();
          return;
        case TestDiscoveryProtocolDataListener.NAMES_DICTIONARY_PART_MARKER:
          LOG.info("name enumerator part received");
          SocketTestDataReader.readDictionary(new DataInputStream(testDataStream), new SocketTestDataReader() {
            @Override
            protected void processTestName(int testClassId, int testMethodId) {
              throw new UnsupportedOperationException();
            }

            @Override
            protected void processUsedMethod(int classId, int methodId) {
              throw new UnsupportedOperationException();
            }

            @Override
            protected void processEnumeratedName(int id, String name) {
              String previousName = myTestExecutionNameEnumerator.put(id, name);
              LOG.assertTrue(previousName == null || name.equals(previousName));
            }
          });
          break;
        case TestDiscoveryProtocolDataListener.TEST_FINISHED_MARKER:
          LOG.info("test data received");
          IdeaSocketTestDiscoveryDataReader reader = new IdeaSocketTestDiscoveryDataReader(myTestExecutionNameEnumerator);
          SocketTestDataReader.readTestData(new DataInputStream(testDataStream), reader);
          myTestDiscoveryIndex.updateFromData(reader.getTestName(), reader.getUsedMethods(), myModuleName, myFrameworkPrefix);
          break;
      }
    }

  }

  public int getPort() {
    return myPort;
  }

  public void closeForcibly() {
    //myCloseForcibly = true;
  }
}
