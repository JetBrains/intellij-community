/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.model.java.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Bitness;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author nik
 */
public class JdkVersionDetectorImpl extends JdkVersionDetector {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.SdkVersionUtil");
  private static final ActionRunner ACTION_RUNNER = new ActionRunner() {
    @Override
    public Future<?> run(Runnable runnable) {
      return SharedThreadPool.getInstance().executeOnPooledThread(runnable);
    }
  };

  @Override
  @Nullable
  public String detectJdkVersion(String homePath) {
    return detectJdkVersion(homePath, ACTION_RUNNER);
  }

  @Nullable
  public String detectJdkVersion(String homePath, final ActionRunner actionRunner) {
    JdkVersionInfo info = detectJdkVersionInfo(homePath, actionRunner);
    if (info != null) {
      return info.getVersion();
    }
    return null;
  }

  @Override
  public JdkVersionInfo detectJdkVersionInfo(String homePath) {
    return detectJdkVersionInfo(homePath, ACTION_RUNNER);
  }

  @Override
  public JdkVersionInfo detectJdkVersionInfo(String homePath, ActionRunner actionRunner) {
    String[] command = {homePath + File.separator + "bin" + File.separator + "java", "-version"};
    return readVersionInfoFromProcessOutput(homePath, command, null, actionRunner);
  }

  public String readVersionFromProcessOutput(String homePath, String[] command, String versionLineMarker,
                                             ActionRunner actionRunner) {
    JdkVersionInfo info = readVersionInfoFromProcessOutput(homePath, command, versionLineMarker, actionRunner);
    if (info != null) {
      return info.getVersion();
    }
    return null;
  }

  private static JdkVersionInfo readVersionInfoFromProcessOutput(String homePath, String[] command, String versionLineMarker, ActionRunner actionRunner) {
    if (homePath == null || !new File(homePath).exists()) {
      return null;
    }
    try {
      //noinspection HardCodedStringLiteral
      Process process = Runtime.getRuntime().exec(command);
      VersionParsingThread parsingThread = new VersionParsingThread(process.getErrorStream(), versionLineMarker);
      final Future<?> parsingThreadFuture = actionRunner.run(parsingThread);
      ReadStreamThread readThread = new ReadStreamThread(process.getInputStream());
      actionRunner.run(readThread);

      try {
        try {
          process.waitFor();
        }
        catch (InterruptedException e) {
          LOG.info(e);
          process.destroy();
        }
      }
      finally {
        try {
          parsingThreadFuture.get();
        }
        catch (Exception e) {
          LOG.info(e);
        }
      }
      String version = parsingThread.getVersion();
      if (version != null) {
        return new JdkVersionInfo(version, parsingThread.getBitness());
      }
    }
    catch (IOException ex) {
      LOG.info(ex);
    }
    return null;
  }

  public static class ReadStreamThread implements Runnable {
    private final InputStream myStream;

    protected ReadStreamThread(InputStream stream) {
      myStream = stream;
    }

    public void run() {
      try {
        while (true) {
          int b = myStream.read();
          if (b == -1) break;
        }
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
  }

  public static class VersionParsingThread implements Runnable {
    private Reader myReader;
    private final InputStream myStream;
    private boolean mySkipLF = false;
    private final String myVersionLineMarker;

    private final AtomicReference<String> myVersionString = new AtomicReference<String>();
    private final AtomicReference<Bitness> myBitness = new AtomicReference<Bitness>(Bitness.x32);
    private static final String VERSION_LINE_MARKER = "version";
    private static final String BITNESS_64_MARKER = "64-Bit";

    protected VersionParsingThread(InputStream input, String versionLineMarker) {
      myStream = input;
      myVersionLineMarker = versionLineMarker != null ? versionLineMarker : VERSION_LINE_MARKER;
    }

    Bitness getBitness() {
      return myBitness.get();
    }

    String getVersion() {
      return myVersionString.get();
    }

    public void run() {
      try {
        myReader = new InputStreamReader(myStream);
        while (true) {
          String line = readLine();
          if (line == null) return;
          if (line.contains(myVersionLineMarker)) {
            myVersionString.set(line);
          }
          if (line.contains(BITNESS_64_MARKER)) {
            myBitness.set(Bitness.x64);
          }
        }
      }
      catch (IOException e) {
        LOG.info(e);
      }
      finally {
        if (myReader != null){
          try {
            myReader.close();
          }
          catch (IOException e) {
            LOG.info(e);
          }
        }
      }
    }

    private String readLine() throws IOException {
      boolean first = true;
      StringBuilder buffer = new StringBuilder();
      while (true) {
        int c = myReader.read();
        if (c == -1) break;
        first = false;
        if (c == '\n') {
          if (mySkipLF) {
            mySkipLF = false;
            continue;
          }
          break;
        }
        else if (c == '\r') {
          mySkipLF = true;
          break;
        }
        else {
          mySkipLF = false;
          buffer.append((char)c);
        }
      }
      if (first) return null;
      String s = buffer.toString();
      //if (Diagnostic.TRACE_ENABLED){
      //  Diagnostic.trace(s);
      //}
      return s;
    }
  }
}
