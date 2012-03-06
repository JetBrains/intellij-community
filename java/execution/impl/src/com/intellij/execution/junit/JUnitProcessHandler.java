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
package com.intellij.execution.junit;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.junit2.segments.Extractor;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;
import java.nio.charset.Charset;

/**
 * @author dyoma
 */
public class JUnitProcessHandler extends OSProcessHandler {
  private final Extractor myOut;
  private final Extractor myErr;
  private final Charset myCharset;

  public JUnitProcessHandler(@NotNull Process process, final String commandLine, @NotNull Charset charset) {
    super(process, commandLine);
    myOut = new Extractor(getProcess().getInputStream(), charset);
    myErr = new Extractor(getProcess().getErrorStream(), charset);
    myCharset = charset;
  }

  @Override
  protected Reader createProcessOutReader() {
    return myOut.createReader();
  }

  @Override
  protected Reader createProcessErrReader() {
    return myErr.createReader();
  }

  public Extractor getErr() {
    return myErr;
  }

  public Extractor getOut() {
    return myOut;
  }

  @Override
  public Charset getCharset() {
    return myCharset;
  }

  public static JUnitProcessHandler runCommandLine(final GeneralCommandLine commandLine) throws ExecutionException {
    final JUnitProcessHandler processHandler = new JUnitProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString(),
                                                                 commandLine.getCharset());
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }

  @Override
  protected void notifyProcessTerminated(int exitCode) {
    super.notifyProcessTerminated(exitCode);
    Disposer.dispose(myOut);
    Disposer.dispose(myErr);
  }

  @Override
  protected void notifyProcessDetached() {
    super.notifyProcessDetached();
    Disposer.dispose(myOut);
    Disposer.dispose(myErr);
  }
}
