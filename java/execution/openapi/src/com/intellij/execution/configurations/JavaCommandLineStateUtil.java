package com.intellij.execution.configurations;

import com.intellij.execution.process.DefaultJavaProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.ExecutionException;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class JavaCommandLineStateUtil {
  private JavaCommandLineStateUtil() {
  }

  public static OSProcessHandler startProcess(@NotNull final GeneralCommandLine commandLine) throws ExecutionException {
    final DefaultJavaProcessHandler processHandler = new DefaultJavaProcessHandler(commandLine);
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }
}
