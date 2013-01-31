/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * OS-independent way of executing external processes with complex parameters.
 *
 * Main idea of the class is to accept parameters "as-is", just as they should look to an external process, and quote/escape them
 * as required by the underlying platform.
 */
public class GeneralCommandLine implements UserDataHolder {
  /** @deprecated use {@linkplain #inescapableQuote(String)} (to remove in IDEA 13) */
  @SuppressWarnings("UnusedDeclaration") public static Key<Boolean> DO_NOT_ESCAPE_QUOTES = Key.create("GeneralCommandLine.do.not.escape.quotes");

  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.configurations.GeneralCommandLine");
  private static final char QUOTE = '\uEFEF';

  private String myExePath = null;
  private File myWorkDirectory = null;
  private Map<String, String> myEnvParams = null;
  private boolean myPassParentEnvironment = true;
  private final ParametersList myProgramParams = new ParametersList();
  private Charset myCharset = CharsetToolkit.getDefaultSystemCharset();
  private boolean myRedirectErrorStream = false;
  private Map<Object, Object> myUserData = null;

  public GeneralCommandLine() { }

  public GeneralCommandLine(final String... command) {
    this(Arrays.asList(command));
  }

  public GeneralCommandLine(final List<String> command) {
    final int size = command.size();
    if (size > 0) {
      setExePath(command.get(0));
      if (size > 1) {
        addParameters(command.subList(1, size));
      }
    }
  }

  public String getExePath() {
    return myExePath;
  }

  public void setExePath(@NotNull @NonNls final String exePath) {
    myExePath = exePath.trim();
  }

  public File getWorkDirectory() {
    return myWorkDirectory;
  }

  public void setWorkDirectory(@Nullable @NonNls final String path) {
    setWorkDirectory(path != null ? new File(path) : null);
  }

  public void setWorkDirectory(@Nullable final File workDirectory) {
    myWorkDirectory = workDirectory;
  }

  @Nullable
  public Map<String, String> getEnvParams() {
    return myEnvParams;
  }

  public void setEnvParams(@Nullable final Map<String, String> envParams) {
    myEnvParams = envParams;
  }

  public void setPassParentEnvs(final boolean passParentEnvironment) {
    myPassParentEnvironment = passParentEnvironment;
  }

  public boolean isPassParentEnvironment() {
    return myPassParentEnvironment;
  }

  public void addParameters(final String... parameters) {
    for (String parameter : parameters) {
      addParameter(parameter);
    }
  }

  public void addParameters(@NotNull final List<String> parameters) {
    for (final String parameter : parameters) {
      addParameter(parameter);
    }
  }

  public void addParameter(@NotNull @NonNls final String parameter) {
    myProgramParams.add(parameter);
  }

  public ParametersList getParametersList() {
    return myProgramParams;
  }

  @NotNull
  public Charset getCharset() {
    return myCharset;
  }

  public void setCharset(@NotNull final Charset charset) {
    myCharset = charset;
  }

  public void setRedirectErrorStream(final boolean redirectErrorStream) {
    myRedirectErrorStream = redirectErrorStream;
  }

  /**
   * Returns string representation of this command line.<br/>
   * Warning: resulting string is not OS-dependent - <b>do not</b> use it for executing this command line.
   *
   * @return single-string representation of this command line.
   */
  public String getCommandLineString() {
    return getCommandLineString(null);
  }

  /**
   * Returns string representation of this command line.<br/>
   * Warning: resulting string is not OS-dependent - <b>do not</b> use it for executing this command line.
   *
   * @param exeName use this executable name instead of given by {@link #setExePath(String)}
   * @return single-string representation of this command line.
   */
  public String getCommandLineString(@Nullable final String exeName) {
    final List<String> commands = new ArrayList<String>();
    if (exeName != null) {
      commands.add(exeName);
    }
    else if (myExePath != null) {
      commands.add(myExePath);
    }
    else {
      commands.add("<null>");
    }
    commands.addAll(myProgramParams.getList());
    return ParametersList.join(commands);
  }

  /**
   * Prepares command (quotes and escapes all arguments) and returns it as a newline-separated list
   * (suitable e.g. for passing in a environment variable).
   *
   * @return command as a newline-separated list.
   */
  @NotNull
  public String getPreparedCommandLine() {
    return StringUtil.join(prepareCommands(), "\n");
  }

  public Process createProcess() throws ExecutionException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Executing [" + getCommandLineString() + "]");
    }

    String[] commands;
    try {
      checkWorkingDirectory();

      commands = prepareCommands();
      if (StringUtil.isEmptyOrSpaces(commands[0])) {
        throw new ExecutionException(IdeBundle.message("run.configuration.error.executable.not.specified"));
      }
    }
    catch (ExecutionException e) {
      LOG.warn(e);
      throw e;
    }

    try {
      ProcessBuilder builder = new ProcessBuilder(commands);
      Map<String, String> environment = builder.environment();
      setupEnvironment(environment);
      builder.directory(myWorkDirectory);
      builder.redirectErrorStream(myRedirectErrorStream);
      return builder.start();
    }
    catch (IOException e) {
      LOG.warn(e);
      throw new ProcessNotCreatedException(e.getMessage(), e, this);
    }
  }

  private void checkWorkingDirectory() throws ExecutionException {
    if (myWorkDirectory == null) {
      return;
    }
    if (!myWorkDirectory.exists()) {
      throw new ExecutionException(IdeBundle.message("run.configuration.error.working.directory.does.not.exist", myWorkDirectory.getAbsolutePath()));
    }
    if (!myWorkDirectory.isDirectory()) {
      throw new ExecutionException(IdeBundle.message("run.configuration.error.working.directory.not.directory"));
    }
  }

  private String[] prepareCommands() {
    final List<String> parameters = myProgramParams.getList();
    final String[] result = new String[parameters.size() + 1];
    result[0] = myExePath != null ? prepareCommand(FileUtil.toSystemDependentName(myExePath)) : null;
    for (int i = 0; i < parameters.size(); i++) {
      result[i + 1] = prepareCommand(parameters.get(i));
    }
    return result;
  }

  // please keep in sync with com.intellij.rt.execution.junit.ProcessBuilder.prepareCommand()
  private static String prepareCommand(String parameter) {
    if (SystemInfo.isWindows) {
      if (parameter.contains("\"")) {
        parameter = StringUtil.replace(parameter, "\"", "\\\"");
      }
      else if (parameter.length() == 0) {
        parameter = "\"\"";
      }
    }

    if (parameter.length() >= 2 && parameter.charAt(0) == QUOTE && parameter.charAt(parameter.length() - 1) == QUOTE) {
      parameter = '"' + parameter.substring(1, parameter.length() - 1) + '"';
    }

    return parameter;
  }

  private void setupEnvironment(final Map<String, String> environment) {
    if (!myPassParentEnvironment) {
      environment.clear();
    }
    else if (SystemInfo.isMac) {
      String pathEnvVarValue = PathEnvironmentVariableUtil.getFixedPathEnvVarValueOnMac();
      if (pathEnvVarValue != null) {
        environment.put(PathEnvironmentVariableUtil.PATH_ENV_VAR_NAME, pathEnvVarValue);
      }
    }
    if (myEnvParams != null) {
      if (SystemInfo.isWindows) {
        THashMap<String, String> envVars = new THashMap<String, String>(CaseInsensitiveStringHashingStrategy.INSTANCE);
        envVars.putAll(environment);
        envVars.putAll(myEnvParams);
        environment.clear();
        environment.putAll(envVars);
      }
      else {
        environment.putAll(myEnvParams);
      }
    }
  }

  /**
   * Normally, double quotes in parameters are escaped so they arrive to a called program as-is.
   * But some commands (e.g. {@code 'cmd /c start "title" ...'}) should get they quotes non-escaped.
   * Wrapping a parameter by this method (instead of using quotes) will do exactly this.
   *
   * @see com.intellij.execution.util.ExecUtil#getTerminalCommand(String, String)
   */
  @NotNull
  public static String inescapableQuote(@NotNull String parameter) {
    return QUOTE + parameter + QUOTE;
  }

  @Override
  public String toString() {
    return myExePath + " " + myProgramParams;
  }

  @Override
  public <T> T getUserData(@NotNull final Key<T> key) {
    if (myUserData != null) {
      @SuppressWarnings({"UnnecessaryLocalVariable", "unchecked"}) final T t = (T)myUserData.get(key);
      return t;
    }
    return null;
  }

  @Override
  public <T> void putUserData(@NotNull final Key<T> key, @Nullable final T value) {
    if (myUserData == null) {
      myUserData = ContainerUtil.newHashMap();
    }
    myUserData.put(key, value);
  }
}
