/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.util.SpinAllocator;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;

import java.io.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 30, 2004
 */
public class CompilerParsingThread implements Runnable, OutputParser.Callback {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.JavaCompilerParsingThread");
  @NonNls public static final String TERMINATION_STRING = "__terminate_read__";
  private final Reader myCompilerOutStreamReader;
  private Process myProcess;
  private final OutputParser myOutputParser;
  private final boolean myTrimLines;
  private boolean mySkipLF = false;
  private Throwable myError = null;
  private final boolean myIsUnitTestMode;
  private FileObject myClassFileToProcess = null;
  private String myLastReadLine = null;
  private volatile boolean myProcessExited = false;
  private final CompileContext myContext;

  public CompilerParsingThread(Process process, OutputParser outputParser, final boolean readErrorStream, boolean trimLines, CompileContext context) {
    myProcess = process;
    myOutputParser = outputParser;
    myTrimLines = trimLines;
    InputStream stream = readErrorStream ? process.getErrorStream() : process.getInputStream();
    myCompilerOutStreamReader = stream == null ? null : new BufferedReader(new InputStreamReader(stream), 16384);
    myIsUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    myContext = context;
  }

  volatile boolean processing;
  public void run() {
    processing = true;
    try {
      while (true) {
        if (!myIsUnitTestMode && myProcess == null) {
          break;
        }
        if (myProcessExited || isCanceled()) {
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
      e.printStackTrace();
      myError = e;
      LOG.info(e);
    }
    finally {
      killProcess();
      processing = false;
    }
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
    return myLastReadLine;
  }

  public final void fileGenerated(FileObject path) {
    // javac first logs file generated, then starts to write the file to disk,
    // so this thread sometimes can stumble on not yet existing file,
    // hence this complex logic
    FileObject previousPath = myClassFileToProcess;
    myClassFileToProcess = path;
    if (previousPath != null) {
      try {
        processCompiledClass(previousPath);
      }
      catch (CacheCorruptedException e) {
        myError = e;
        LOG.info(e);
        killProcess();
      }
    }
  }

  protected boolean isCanceled() {
    return myContext.getProgressIndicator().isCanceled();
  }

  public void setProgressText(String text) {
    myContext.getProgressIndicator().setText(text);
  }

  public void fileProcessed(String path) {
  }

  public void message(CompilerMessageCategory category, String message, String url, int lineNum, int columnNum) {
    myContext.addMessage(category, message, url, lineNum, columnNum);
  }

  protected void processCompiledClass(final FileObject classFileToProcess) throws CacheCorruptedException {
  }


  private String readLine(final Reader reader) {
    StringBuilder buffer;
    boolean releaseBuffer = true;
    try {
      buffer = StringBuilderSpinAllocator.alloc();
    }
    catch (SpinAllocator.AllocatorExhaustedException e) {
      LOG.info(e);
      buffer = new StringBuilder();
      releaseBuffer = false;
    }

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
      if (releaseBuffer) {
        StringBuilderSpinAllocator.dispose(buffer);
      }
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

  private boolean isProcessTerminated() {
    return myProcessExited;
  }

  public void setProcessTerminated(final boolean procesExited) {
    myProcessExited = procesExited;
  }
}
