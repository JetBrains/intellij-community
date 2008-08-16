package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;

import java.io.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 30, 2004
 */
public abstract class CompilerParsingThread implements Runnable, OutputParser.Callback {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.JavaCompilerParsingThread");
  @NonNls public static final String TERMINATION_STRING = "__terminate_read__";
  private Reader myCompilerOutStreamReader;
  private Process myProcess;
  private OutputParser myOutputParser;
  private final boolean myTrimLines;
  private boolean mySkipLF = false;
  private Throwable myError = null;
  private final boolean myIsUnitTestMode;
  private String myClassFileToProcess = null;
  private String myLastReadLine = null;
  private boolean myProcessExited = false;


  public CompilerParsingThread(Process process, OutputParser outputParser, final boolean readErrorStream, boolean trimLines) {
    myProcess = process;
    myOutputParser = outputParser;
    myTrimLines = trimLines;
    InputStream stream = readErrorStream ? process.getErrorStream() : process.getInputStream();
    myCompilerOutStreamReader = stream == null ? null : new BufferedReader(new InputStreamReader(stream), 16384);
    myIsUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
  }

  public void run() {
    try {
      while (true) {
        if (!myIsUnitTestMode && myProcess == null) {
          break;
        }
        if (isCanceled()) {
          break;
        }
        if (!myOutputParser.processMessageLine(this)) {
          break;
        }
      }
      if (myClassFileToProcess != null) {
        processCompiledClass(myClassFileToProcess);
        myClassFileToProcess = null;
      }
    }
    catch (Throwable e) {
      myError = e;
      LOG.info(e);
    }
    killProcess();
  }

  private void killProcess() {
    if (myProcess != null) {
      myProcess.destroy();
      myProcess = null;
    }
  }

  public Throwable getError() {
    return myError;
  }

  public String getCurrentLine() {
    return myLastReadLine;
  }

  public final String getNextLine() {
    try {
      final String line = readLine(myCompilerOutStreamReader);
      if (LOG.isDebugEnabled()) {
        LOG.debug("LIne read: #" + line + "#");
      }
      if (TERMINATION_STRING.equals(line)) {
        myLastReadLine = null;
      }
      else {
        myLastReadLine = line == null ? null : myTrimLines ? line.trim() : line;
      }
    }
    catch (IOException e) {
      if (LOG.isDebugEnabled()) {
        LOG.error(e);
      }
      myLastReadLine = null;
    }
    return myLastReadLine;
  }

  public final void fileGenerated(String path) {
    String previousPath = myClassFileToProcess;
    myClassFileToProcess = path;
    if (previousPath != null) {
      try {
        processCompiledClass(previousPath);
      }
      catch (CacheCorruptedException e) {
        myError = e;
        killProcess();
      }
    }
  }

  protected abstract boolean isCanceled();

  protected abstract void processCompiledClass(final String classFileToProcess) throws CacheCorruptedException;


  private String readLine(final Reader reader) throws IOException {
    StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      boolean first = true;
      while (true) {
        int c = readNextByte(reader);
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
      if (first) {
        return null;
      }
      return buffer.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
  }

  private int readNextByte(final Reader reader) {
    try {
      while(!reader.ready()) {
        if (isProcessTerminated()) {
          return -1;
        }
        try {
          Thread.sleep(1L);
        }
        catch (InterruptedException ignore) {
        }
      }
      return reader.read();
    }
    catch (IOException e) {
      return -1; // When process terminated Process.getInputStream()'s underlaying stream becomes closed on Linux.
    }
  }

  private synchronized boolean isProcessTerminated() {
    return myProcessExited;
  }

  public synchronized void setProcessTerminated(final boolean procesExited) {
    myProcessExited = procesExited;
  }
}
