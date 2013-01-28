package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @author Sergey Simonchik
 */
public class PathEnvironmentVariableUtil {

  private static final Logger LOG = Logger.getInstance(PathEnvironmentVariableUtil.class);
  public static final String PATH_ENV_VAR_NAME = "PATH";
  private static final int BLOCK_TIMEOUT_MILLIS = 50;

  private static volatile String ourFixedMacPathEnvVarValue;
  static {
    asyncCalcFixedMacPathEnvVarValue();
  }

  private PathEnvironmentVariableUtil() {}

  /**
   * Tries to return a real value for PATH environment variable by parsing the result of
   * {@code /usr/libexec/path_helper -s} command execution.
   * Workaround for http://youtrack.jetbrains.com/issue/IDEA-99154
   */
  @Nullable
  public static String getFixedPathEnvVarValueOnMac() {
    return ourFixedMacPathEnvVarValue;
  }

  @Nullable
  private static String getOriginalPathEnvVarValue() {
    Map<String, String> envVars = EnvironmentUtil.getEnvironmentProperties();
    return envVars.get(PATH_ENV_VAR_NAME);
  }

  private static void asyncCalcFixedMacPathEnvVarValue() {
    if (SystemInfo.isMac) {
      final String originalValue = getOriginalPathEnvVarValue();
      final Semaphore semaphore = new Semaphore(0, true);
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
        @Override
        public void run() {
          try {
            ourFixedMacPathEnvVarValue = findFixedPathEnvVarValue(originalValue);
          }
          finally {
            semaphore.release();
          }
        }
      });
      try {
        semaphore.tryAcquire(BLOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
      }
      catch (InterruptedException e) {
        LOG.info("Unexpected thread interruption", e);
      }
    }
  }

  @Nullable
  private static String findFixedPathEnvVarValue(@Nullable String originalValue) {
    File executable = new File("/usr/libexec/path_helper");
    if (executable.isFile() && executable.canExecute()) {
      GeneralCommandLine commandLine = new GeneralCommandLine();
      try {
        commandLine.setExePath(executable.getAbsolutePath());
        commandLine.addParameter("-s");
        List<String> stdout = executeAndGetStdout(commandLine);
        final String prefix = "PATH=\"";
        final String suffix = "\"; export PATH;";
        for (String line : stdout) {
          if (line.startsWith(prefix) && line.endsWith(suffix)) {
            String value = line.substring(prefix.length(), line.length() - suffix.length());
            return mergePaths(ContainerUtil.newArrayList(originalValue, value));
          }
        }
      } catch (Exception e) {
        LOG.info("Can't execute '" + commandLine.getCommandLineString() + "'", e);
      }
    }
    return null;
  }

  @NotNull
  public static List<String> executeAndGetStdout(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    final Process process = commandLine.createProcess();
    CapturingProcessHandler processHandler = new CapturingProcessHandler(process, CharsetToolkit.UTF8_CHARSET);
    ProcessOutput output = processHandler.runProcess(2000);
    int exitCode = output.getExitCode();
    if (exitCode != 0) {
      throw new ExecutionException("Exit code of '" + commandLine.getCommandLineString()
                                   + "' is " + exitCode);
    }
    if (!output.getStderr().isEmpty()) {
      throw new ExecutionException("Stderr of '" + commandLine.getCommandLineString()
                                   + "': " + output.getStderr());
    }
    return output.getStdoutLines();
  }

  @Nullable
  private static String mergePaths(@NotNull List<String> pathStrings) {
    LinkedHashSet<String> paths = ContainerUtil.newLinkedHashSet();
    for (String pathString : pathStrings) {
      if (pathString != null) {
        List<String> locals = StringUtil.split(pathString, File.pathSeparator, true, true);
        for (String local : locals) {
          paths.add(local);
        }
      }
    }
    if (paths.isEmpty()) {
      return null;
    }
    return StringUtil.join(paths, File.pathSeparator);
  }

}
