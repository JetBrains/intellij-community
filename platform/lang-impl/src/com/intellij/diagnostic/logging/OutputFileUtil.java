/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.diagnostic.logging;

import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

/**
 * User: anna
 * Date: 10/20/11
 */
public class OutputFileUtil {
  private static final String CONSOLE_OUTPUT_FILE_MESSAGE = "Console output is saving to: ";

  private OutputFileUtil() {
  }

  public static void attachDumpListener(final RunConfigurationBase base, final ProcessHandler startedProcess, ExecutionConsole console) {
    if (base.isSaveOutputToFile()) {
      final String outputFilePath = base.getOutputFilePath();
      if (outputFilePath != null) {
        final String filePath = FileUtil.toSystemDependentName(outputFilePath);
        startedProcess.addProcessListener(new ProcessAdapter() {
          private PrintStream myOutput;
          @Override
          public void onTextAvailable(ProcessEvent event, Key outputType) {
            if (base.collectOutputFromProcessHandler() && myOutput != null && outputType != ProcessOutputTypes.SYSTEM) {
              myOutput.print(event.getText());
            }
          }

          @Override
          public void startNotified(ProcessEvent event) {
            try {
              myOutput = new PrintStream(new FileOutputStream(new File(filePath)));
            }
            catch (FileNotFoundException ignored) {
            }
            startedProcess.notifyTextAvailable(CONSOLE_OUTPUT_FILE_MESSAGE + filePath + "\n", ProcessOutputTypes.SYSTEM);
          }

          @Override
          public void processTerminated(ProcessEvent event) {
            startedProcess.removeProcessListener(this);
            if (myOutput != null) {
              myOutput.close();
            }
          }
        });
        if (console instanceof ConsoleView) {
          ((ConsoleView)console).addMessageFilter(new ShowOutputFileFilter());
        }
      }
    }
  }

  private static class ShowOutputFileFilter implements Filter {
    @Override
    public Result applyFilter(String line, int entireLength) {
      if (line.startsWith(CONSOLE_OUTPUT_FILE_MESSAGE)) {
        final String filePath = StringUtil.trimEnd(line.substring(CONSOLE_OUTPUT_FILE_MESSAGE.length()), "\n");

        return new Result(entireLength - filePath.length() - 1, entireLength, new HyperlinkInfo() {
          @Override
          public void navigate(final Project project) {
            final VirtualFile file =
              ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
                @Nullable
                @Override
                public VirtualFile compute() {
                  return LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(filePath));
                }
              });

            if (file != null) {
              file.refresh(false, false);
              ApplicationManager.getApplication().runReadAction(new Runnable() {
                @Override
                public void run() {
                  FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file), true);
                }
              });
            }
          }
        });
      }
      return null;
    }
  }
}
