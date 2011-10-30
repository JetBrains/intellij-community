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

package org.jetbrains.jps.incremental.groovy;

import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.groovy.compiler.rt.GroovycRunner;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 16.04.2007
 */
public class GroovycOSProcessHandler extends BaseOSProcessHandler {
  public static final String GROOVY_COMPILER_IN_OPERATION = "Groovy compiler in operation...";
  private final List<OutputItem> myCompiledItems = new ArrayList<OutputItem>();
  private final Set<File> toRecompileFiles = new HashSet<File>();
  private final List<CompilerMessage> compilerMessages = new ArrayList<CompilerMessage>();
  private final StringBuffer stdErr = new StringBuffer();

  private static final Logger LOG = Logger.getInstance("org.jetbrains.jps.incremental.groovy.GroovycOSProcessHandler");

  public GroovycOSProcessHandler(Process process, String s) {
    super(process, s, null);
  }

  public void notifyTextAvailable(final String text, final Key outputType) {
    super.notifyTextAvailable(text, outputType);

    if (LOG.isDebugEnabled()) {
      LOG.debug("Received from groovyc: " + text);
    }

    if (outputType == ProcessOutputTypes.SYSTEM) {
      return;
    }

    if (outputType == ProcessOutputTypes.STDERR) {
      stdErr.append(StringUtil.convertLineSeparators(text));
      return;
    }


    parseOutput(text);
  }

  private final StringBuffer outputBuffer = new StringBuffer();

  protected void updateStatus(@Nullable String status) { }

  private void parseOutput(String text) {
    final String trimmed = text.trim();

    if (trimmed.startsWith(GroovycRunner.PRESENTABLE_MESSAGE)) {
      updateStatus(trimmed.substring(GroovycRunner.PRESENTABLE_MESSAGE.length()));
      return;
    }

    if (GroovycRunner.CLEAR_PRESENTABLE.equals(trimmed)) {
      updateStatus(null);
      return;
    }


    if (StringUtil.isNotEmpty(text)) {
      outputBuffer.append(text);

      //compiled start marker have to be in the beginning on each string
      if (outputBuffer.indexOf(GroovycRunner.COMPILED_START) != -1) {
        if (outputBuffer.indexOf(GroovycRunner.COMPILED_END) == -1) {
          return;
        }

        final String compiled = handleOutputBuffer(GroovycRunner.COMPILED_START, GroovycRunner.COMPILED_END);
        final List<String> list = StringUtil.split(compiled, GroovycRunner.SEPARATOR);
        String outputPath = list.get(0);
        String sourceFile = list.get(1);

        ContainerUtil.addIfNotNull(getOutputItem(outputPath, sourceFile), myCompiledItems);

      }
      else if (outputBuffer.indexOf(GroovycRunner.TO_RECOMPILE_START) != -1) {
        if (outputBuffer.indexOf(GroovycRunner.TO_RECOMPILE_END) != -1) {
          String url = handleOutputBuffer(GroovycRunner.TO_RECOMPILE_START, GroovycRunner.TO_RECOMPILE_END);
          toRecompileFiles.add(new File(url));
        }
      }
      else if (outputBuffer.indexOf(GroovycRunner.MESSAGES_START) != -1) {
        if (!(outputBuffer.indexOf(GroovycRunner.MESSAGES_END) != -1)) {
          return;
        }

        text = handleOutputBuffer(GroovycRunner.MESSAGES_START, GroovycRunner.MESSAGES_END);

        List<String> tokens = StringUtil.split(text, GroovycRunner.SEPARATOR);
        LOG.assertTrue(tokens.size() > 4, "Wrong number of output params");

        String category = tokens.get(0);
        String message = tokens.get(1);
        String url = tokens.get(2);
        String lineNum = tokens.get(3);
        String columnNum = tokens.get(4);

        int lineInt;
        int columnInt;

        try {
          lineInt = Integer.parseInt(lineNum);
          columnInt = Integer.parseInt(columnNum);
        }
        catch (NumberFormatException e) {
          LOG.error(e);
          lineInt = 0;
          columnInt = 0;
        }

        BuildMessage.Kind kind = category.equals(org.jetbrains.groovy.compiler.rt.CompilerMessage.ERROR)
                                 ? BuildMessage.Kind.ERROR
                                 : category.equals(org.jetbrains.groovy.compiler.rt.CompilerMessage.WARNING)
                                   ? BuildMessage.Kind.WARNING
                                   : BuildMessage.Kind.INFO;

        compilerMessages.add(new CompilerMessage("Groovyc", kind, message, url, -1, -1, -1, lineInt, columnInt));
      }
    }
  }

  private String handleOutputBuffer(String startMarker, String endMarker) {
    final int start = outputBuffer.indexOf(startMarker);
    final int end = outputBuffer.indexOf(endMarker);
    if (start > end) {
      throw new AssertionError("Malformed Groovyc output: " + outputBuffer.toString());
    }

    String text = outputBuffer.substring(start + startMarker.length(), end);

    outputBuffer.delete(start, end + endMarker.length());

    return text.trim();
  }

  @Nullable
  private static OutputItem getOutputItem(final String outputPath, final String sourceFile) {
    return new OutputItem(outputPath, sourceFile);
  }

  public List<OutputItem> getSuccessfullyCompiled() {
    return myCompiledItems;
  }

  public Set<File> getToRecompileFiles() {
    return toRecompileFiles;
  }

  public List<CompilerMessage> getCompilerMessages() {
    return compilerMessages;
  }

  public StringBuffer getStdErr() {
    return stdErr;
  }

  public static class OutputItem {
    public final String outputPath;
    public final String sourcePath;

    public OutputItem(String outputPath, String sourceFileName) {
      this.outputPath = outputPath;
      sourcePath = sourceFileName;
    }
  }

}
