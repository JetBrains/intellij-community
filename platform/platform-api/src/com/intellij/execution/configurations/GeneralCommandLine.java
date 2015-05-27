/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Platform;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

/**
 * OS-independent way of executing external processes with complex parameters.
 * <p/>
 * Main idea of the class is to accept parameters "as-is", just as they should look to an external process, and quote/escape them
 * as required by the underlying platform.
 *
 * @see com.intellij.execution.process.OSProcessHandler
 */
public class GeneralCommandLine implements UserDataHolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.configurations.GeneralCommandLine");

  private String myExePath = null;
  private File myWorkDirectory = null;
  private final Map<String, String> myEnvParams = new MyTHashMap();
  private boolean myPassParentEnvironment = true;
  private final ParametersList myProgramParams = new ParametersList();
  private Charset myCharset = CharsetToolkit.getDefaultSystemCharset();
  private boolean myRedirectErrorStream = false;
  private Map<Object, Object> myUserData = null;

  public GeneralCommandLine() { }

  public GeneralCommandLine(@NotNull String... command) {
    this(Arrays.asList(command));
  }

  public GeneralCommandLine(@NotNull List<String> command) {
    int size = command.size();
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

  @NotNull
  public GeneralCommandLine withExePath(@NotNull String exePath) {
    myExePath = exePath.trim();
    return this;
  }

  public void setExePath(@NotNull String exePath) {
    withExePath(exePath);
  }

  public File getWorkDirectory() {
    return myWorkDirectory;
  }

  @NotNull
  public GeneralCommandLine withWorkDirectory(@Nullable String path) {
    return withWorkDirectory(path != null ? new File(path) : null);
  }

  @NotNull
  public GeneralCommandLine withWorkDirectory(@Nullable File workDirectory) {
    myWorkDirectory = workDirectory;
    return this;
  }

  public void setWorkDirectory(@Nullable String path) {
    withWorkDirectory(path);
  }

  public void setWorkDirectory(@Nullable File workDirectory) {
    withWorkDirectory(workDirectory);
  }

  /**
   * Note: the map returned is forgiving to passing null values into putAll().
   */
  @NotNull
  public Map<String, String> getEnvironment() {
    return myEnvParams;
  }

  @NotNull
  public GeneralCommandLine withEnvironment(@Nullable Map<String, String> environment) {
    if (environment != null) {
      getEnvironment().putAll(environment);
    }
    return this;
  }

  public boolean isPassParentEnvironment() {
    return myPassParentEnvironment;
  }

  @NotNull
  public GeneralCommandLine withPassParentEnvironment(boolean passParentEnvironment) {
    myPassParentEnvironment = passParentEnvironment;
    return this;
  }

  public void setPassParentEnvironment(boolean passParentEnvironment) {
    withPassParentEnvironment(passParentEnvironment);
  }

  /**
   * @return unmodifiable map of the parent environment, that will be passed to the process if isPassParentEnvironment() == true
   */
  @NotNull
  public Map<String, String> getParentEnvironment() {
    return PlatformUtils.isAppCode() ? System.getenv() // Temporarily fix for OC-8606
                                     : EnvironmentUtil.getEnvironmentMap();
  }

  public void addParameters(String... parameters) {
    for (String parameter : parameters) {
      addParameter(parameter);
    }
  }

  public void addParameters(@NotNull List<String> parameters) {
    for (String parameter : parameters) {
      addParameter(parameter);
    }
  }

  public void addParameter(@NotNull String parameter) {
    myProgramParams.add(parameter);
  }

  public ParametersList getParametersList() {
    return myProgramParams;
  }

  @NotNull
  public Charset getCharset() {
    return myCharset;
  }

  @NotNull
  public GeneralCommandLine withCharset(@NotNull Charset charset) {
    myCharset = charset;
    return this;
  }

  public void setCharset(@NotNull Charset charset) {
    withCharset(charset);
  }

  public boolean isRedirectErrorStream() {
    return myRedirectErrorStream;
  }

  @NotNull
  public GeneralCommandLine withRedirectErrorStream(boolean redirectErrorStream) {
    myRedirectErrorStream = redirectErrorStream;
    return this;
  }

  public void setRedirectErrorStream(boolean redirectErrorStream) {
    withRedirectErrorStream(redirectErrorStream);
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
  public String getCommandLineString(@Nullable String exeName) {
    return ParametersList.join(getCommandLineList(exeName));
  }

  public List<String> getCommandLineList(@Nullable String exeName) {
    List<String> commands = new ArrayList<String>();
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
    return commands;
  }

  /**
   * Prepares command (quotes and escapes all arguments) and returns it as a newline-separated list
   * (suitable e.g. for passing in an environment variable).
   *
   * @param platform a target platform
   * @return command as a newline-separated list.
   */
  @NotNull
  public String getPreparedCommandLine(@NotNull Platform platform) {
    String exePath = myExePath != null ? myExePath : "";
    return StringUtil.join(CommandLineUtil.toCommandLine(exePath, myProgramParams.getList(), platform), "\n");
  }

  @NotNull
  public Process createProcess() throws ExecutionException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Executing [" + getCommandLineString() + "]");
    }

    List<String> commands;
    try {
      checkWorkingDirectory();

      if (StringUtil.isEmptyOrSpaces(myExePath)) {
        throw new ExecutionException(IdeBundle.message("run.configuration.error.executable.not.specified"));
      }

      commands = CommandLineUtil.toCommandLine(myExePath, myProgramParams.getList());
    }
    catch (ExecutionException e) {
      LOG.info(e);
      throw e;
    }

    try {
      return startProcess(commands);
    }
    catch (IOException e) {
      LOG.info(e);
      throw new ProcessNotCreatedException(e.getMessage(), e, this);
    }
  }

  @NotNull
  protected Process startProcess(@NotNull List<String> commands) throws IOException {
    ProcessBuilder builder = new ProcessBuilder(commands);
    setupEnvironment(builder.environment());
    builder.directory(myWorkDirectory);
    builder.redirectErrorStream(myRedirectErrorStream);
    return builder.start();
  }

  private void checkWorkingDirectory() throws ExecutionException {
    if (myWorkDirectory == null) {
      return;
    }
    if (!myWorkDirectory.exists()) {
      throw new ExecutionException(
        IdeBundle.message("run.configuration.error.working.directory.does.not.exist", myWorkDirectory.getAbsolutePath()));
    }
    if (!myWorkDirectory.isDirectory()) {
      throw new ExecutionException(IdeBundle.message("run.configuration.error.working.directory.not.directory"));
    }
  }

  protected void setupEnvironment(@NotNull Map<String, String> environment) {
    environment.clear();

    if (myPassParentEnvironment) {
      environment.putAll(getParentEnvironment());
    }
    
    if (!myEnvParams.isEmpty()) {
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
    return CommandLineUtil.specialQuote(parameter);
  }

  @Override
  public String toString() {
    return myExePath + " " + myProgramParams;
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    if (myUserData != null) {
      @SuppressWarnings({"UnnecessaryLocalVariable", "unchecked"}) T t = (T)myUserData.get(key);
      return t;
    }
    return null;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    if (myUserData == null) {
      myUserData = ContainerUtil.newHashMap();
    }
    myUserData.put(key, value);
  }

  private static class MyTHashMap extends THashMap<String, String> {
    @Override
    public String put(String key, String value) {
      if (key == null || value == null) {
        LOG.error(new Exception("Nulls are not allowed"));
        return null;
      }
      if (key.isEmpty()) {
        // Windows: passing an environment variable with empty name causes "CreateProcess error=87, The parameter is incorrect"
        LOG.warn("Skipping environment variable with empty name, value: " + value);
        return null;
      }
      return super.put(key, value);
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> map) {
      if (map != null) {
        super.putAll(map);
      }
    }
  }
}
