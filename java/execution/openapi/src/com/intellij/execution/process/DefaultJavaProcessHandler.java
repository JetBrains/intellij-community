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
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineBuilder;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

public class DefaultJavaProcessHandler extends OSProcessHandler {
  private final Charset myCharset;

  public DefaultJavaProcessHandler(final JavaParameters javaParameters) throws ExecutionException {
    this(CommandLineBuilder.createFromJavaParameters(javaParameters));
  }

  public DefaultJavaProcessHandler(final GeneralCommandLine commandLine) throws ExecutionException {
    this(commandLine.createProcess(), commandLine.getCommandLineString(), commandLine.getCharset());
  }

  public DefaultJavaProcessHandler(final Process process, final String commandLine, @NotNull final Charset charset) {
    super(process, commandLine);
    myCharset = charset;
  }

  public Charset getCharset() {
    return myCharset;
  }
}
