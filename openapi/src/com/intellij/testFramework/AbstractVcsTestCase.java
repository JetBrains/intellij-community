package com.intellij.testFramework;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class AbstractVcsTestCase {
  protected Project myProject;
  protected VirtualFile myWorkingCopyDir;
  protected File myClientBinaryPath;

  protected RunResult runClient(String exeName, @Nullable String stdin, String[] commandLine) throws IOException {
    final List<String> arguments = new ArrayList<String>();
    arguments.add(new File(myClientBinaryPath, exeName).toString());
    Collections.addAll(arguments, commandLine);
    Process clientProcess = new ProcessBuilder().command(arguments).start();

    final RunResult result = new RunResult();

    if (stdin != null) {
      OutputStream outputStream = clientProcess.getOutputStream();
      try {
        byte[] bytes = stdin.getBytes();
        outputStream.write(bytes);
      }
      finally {
        outputStream.close();
      }
    }

    OSProcessHandler handler = new OSProcessHandler(clientProcess, "") {
      public Charset getCharset() {
        return CharsetToolkit.getDefaultSystemCharset();
      }
    };
    handler.addProcessListener(new ProcessAdapter() {
      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
        if (outputType == ProcessOutputTypes.STDOUT) {
          result.stdOut += event.getText();
        }
        else if (outputType == ProcessOutputTypes.STDERR) {
          result.stdErr += event.getText();
        }
      }
    });
    handler.startNotify();
    handler.waitFor();
    result.exitCode = clientProcess.exitValue();
    return result;
  }

  protected class RunResult {
    public int exitCode = -1;
    public String stdOut = "";
    public String stdErr = "";
  }
}