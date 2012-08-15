package org.jetbrains.jps.model.java.impl;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.*;
import java.util.concurrent.Future;

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
    String[] command = {homePath + File.separator + "bin" + File.separator + "java", "-version"};
    return readVersionFromProcessOutput(homePath, command, "version", actionRunner);
  }

  public String readVersionFromProcessOutput(String homePath, String[] command, String versionLineMarker,
                                                    ActionRunner actionRunner) {
    if (homePath == null || !new File(homePath).exists()) {
      return null;
    }
    final String[] versionString = new String[1];
    try {
      //noinspection HardCodedStringLiteral
      Process process = Runtime.getRuntime().exec(command);
      VersionParsingThread parsingThread = new VersionParsingThread(process.getErrorStream(), versionString, versionLineMarker);
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
    }
    catch (IOException ex) {
      LOG.info(ex);
    }
    return versionString[0];
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
    private final String[] myVersionString;
    private final String myVersionLineMarker;

    protected VersionParsingThread(InputStream input, String[] versionString, String versionLineMarker) {
      myStream = input;
      myVersionString = versionString;
      myVersionLineMarker = versionLineMarker;
    }

    public void run() {
      try {
        myReader = new InputStreamReader(myStream);
        while (true) {
          String line = readLine();
          if (line == null) return;
          if (line.contains(myVersionLineMarker)) {
            myVersionString[0] = line;
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
