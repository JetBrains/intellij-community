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

  /**
   * Note: the map returned is forgiving to passing null values into putAll().
   */
  @NotNull
  public Map<String, String> getEnvironment() {
    return myEnvParams;
  }

  /**
   * @deprecated use {@link #getEnvironment()} (to remove in IDEA 14)
   */
  @SuppressWarnings("unused")
  public Map<String, String> getEnvParams() {
    return getEnvironment();
  }

  /**
   * @deprecated use {@link #getEnvironment()} (to remove in IDEA 14)
   */
  @SuppressWarnings("unused")
  public void setEnvParams(@Nullable Map<String, String> envParams) {
    myEnvParams.clear();
    if (envParams != null) {
      myEnvParams.putAll(envParams);
    }
  }

  public void setPassParentEnvironment(boolean passParentEnvironment) {
    myPassParentEnvironment = passParentEnvironment;
  }

  /**
   * @deprecated use {@link #setPassParentEnvironment(boolean)} (to remove in IDEA 14)
   */
  @SuppressWarnings({"unused", "SpellCheckingInspection"})
  public void setPassParentEnvs(boolean passParentEnvironment) {
    setPassParentEnvironment(passParentEnvironment);
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

  public boolean isRedirectErrorStream() {
    return myRedirectErrorStream;
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
      LOG.warn(e);
      throw e;
    }

    try {
      return startProcess(commands);
    }
    catch (IOException e) {
      LOG.warn(e);
      throw new ProcessNotCreatedException(e.getMessage(), e, this);
    }
  }

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
      environment.putAll(PlatformUtils.isAppCode() ? System.getenv() // Temporarily fix for OC-8606
                                                   : EnvironmentUtil.getEnvironmentMap());
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

  private static class MyTHashMap extends THashMap<String, String> {
    @Override
    public void putAll(Map<? extends String, ? extends String> map) {
      if (map != null) {
        super.putAll(map);
      }
    }
  }
}
