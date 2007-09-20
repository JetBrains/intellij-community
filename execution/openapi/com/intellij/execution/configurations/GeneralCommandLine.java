/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class GeneralCommandLine {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.configurations.GeneralCommandLine");
  private Map<String, String> myEnvParams;
  private boolean myPassParentEnvs;
  private String myExePath = null;
  private File myWorkDirectory = null;
  private ParametersList myProgramParams = new ParametersList();
  private Charset myCharset = CharsetToolkit.getDefaultSystemCharset();

  public void setExePath(@NonNls final String exePath) {
    myExePath = quote(exePath.trim());
  }

  public void setWorkDirectory(@NonNls final String path) {
    setWorkingDirectory(path != null? new File(path) : null);
  }

  public void setWorkingDirectory(final File workingDirectory) {
    myWorkDirectory = workingDirectory;
  }

  public void setEnvParams(final Map<String, String> envParams) {
    myEnvParams = envParams;
  }

  public void setCharset(final Charset charset) {
    if (charset == null) {
      LOG.error("null charset");
      return;
    }
    myCharset = charset;
    //noinspection HardCodedStringLiteral
    addParameter("-Dfile.encoding=" + charset.name());
  }

  public void addParameters(final String[] parameters) {
    for (String parameter : parameters) {
      addParameter(parameter);
    }
  }

  public void addParameters(final List<String> parameters) {
    for (final String parameter : parameters) {
      addParameter(parameter);
    }
  }

  public void addParameter(@NonNls final String parameter) {
    LOG.assertTrue(parameter != null);
    myProgramParams.add(quote(parameter));
  }

  public String getCommandLineString() {
    final StringBuffer buffer = new StringBuffer(myExePath);
    appendParams( buffer );
    return buffer.toString();
  }

  public String getCommandLineParams() {
    final StringBuffer buffer = new StringBuffer();
    appendParams( buffer );
    return buffer.toString();
  }

  private void appendParams( StringBuffer buffer )
  {
    for( final String s : myProgramParams.getList() )
      buffer.append(" ").append(s);
  }

  public Charset getCharset() {
    return myCharset;
  }

  public Process createProcess() throws ExecutionException {
    checkWorkingDirectory();
    try {
      final String[] commands = getCommands();
      if(commands[0] == null) throw new CantRunException(ExecutionBundle.message("run.configuration.error.executable.not.specified"));

      return myWorkDirectory != null
             ? Runtime.getRuntime().exec(commands, getEnvParamsArray(), myWorkDirectory)
             : Runtime.getRuntime().exec(commands, getEnvParamsArray());
    }
    catch (IOException e) {
      throw new ProcessNotCreatedException(e.getMessage(), this);
    }
  }

  private void checkWorkingDirectory() throws ExecutionException {
    if (myWorkDirectory == null) {
      return;
    }
    if (!myWorkDirectory.exists()) {
      throw new ExecutionException(
        ExecutionBundle.message("run.configuration.error.working.directory.does.not.exist", myWorkDirectory.getAbsolutePath()));
    }
    if (!myWorkDirectory.isDirectory()) {
      throw new ExecutionException(ExecutionBundle.message("run.configuration.error.working.directory.not.directory"));
    }
  }

  private String[] getEnvParamsArray() {
    if (myEnvParams == null) {
      return null;
    }
    final Map<String, String> envParams = new HashMap<String, String>(myEnvParams);
    if (myPassParentEnvs) {
      envParams.putAll(System.getenv());
    }
    final String[] result = new String[envParams.size()];
    int i=0;
    for (final String key : envParams.keySet()) {
      result[i++] = key + "=" + envParams.get(key).trim();
    }
    return result;
  }

  public String[] getCommands() {
    final List<String> parameters = myProgramParams.getList();
    final String[] result = new String[parameters.size() + 1];
    result[0] = myExePath;
    int index = 1;
    for (Iterator<String> iterator = parameters.iterator(); iterator.hasNext(); index++) {
      result[index] = iterator.next();
    }
    return result;
  }

  public ParametersList getParametersList() {
    return myProgramParams;
  }

  public static String quote(final String parameter) {
    if (parameter.indexOf(' ') < 0) {
      return parameter; // no need to quote
    }
    if (parameter.length() >= 2 && parameter.charAt(0) == '"' && parameter.charAt(parameter.length() - 1) == '"' && !parameter.endsWith("\\\"")) {
      return parameter; // already quoted
    }
    return SystemInfo.isWindows ? "\"" + parameter + "\"" : parameter;
  }

  public GeneralCommandLine clone() {
    final GeneralCommandLine clone = new GeneralCommandLine();
    clone.myCharset = myCharset;
    clone.myExePath = myExePath;
    clone.myWorkDirectory = myWorkDirectory;
    clone.myProgramParams = myProgramParams.clone();
    clone.myEnvParams = myEnvParams != null ? new HashMap<String, String>(myEnvParams) : null;
    return clone;
  }

  public static GeneralCommandLine createFromJavaParameters(final JavaParameters javaParameters) throws CantRunException {
    try {
      return ApplicationManager.getApplication().runReadAction(new Computable<GeneralCommandLine>() {
        public GeneralCommandLine compute() {
          try {
            final GeneralCommandLine commandLine = new GeneralCommandLine();
            final ProjectJdk jdk = javaParameters.getJdk();
            if(jdk == null) {
              throw new CantRunException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"));
            }

            final String exePath = jdk.getVMExecutablePath();
            if(exePath == null) {
              throw new CantRunException(ExecutionBundle.message("run.configuration.cannot.find.vm.executable"));
            }
            commandLine.setExePath(exePath);
            ParametersList parametersList = javaParameters.getVMParametersList();
            commandLine.addParameters(parametersList.getList());
            if (!parametersList.hasProperty("file.encoding")) {
              Charset charset = javaParameters.getCharset();
              if (charset == null) charset = CharsetToolkit.getIDEOptionsCharset();
              if (charset == null) charset = CharsetToolkit.getDefaultSystemCharset();
              commandLine.setCharset(charset);
            }

            if(!parametersList.hasParameter("-classpath") && !parametersList.hasParameter("-cp")){
              commandLine.addParameter("-classpath");
              commandLine.addParameter(javaParameters.getClassPath().getPathsString());
            }

            String mainClass = javaParameters.getMainClass();
            if(mainClass == null) throw new CantRunException(ExecutionBundle.message("main.class.is.not.specified.error.message"));
            commandLine.addParameter(mainClass);
            commandLine.addParameters(javaParameters.getProgramParametersList().getList());
            commandLine.setWorkDirectory(javaParameters.getWorkingDirectory());

            final Map<String, String> env = javaParameters.getEnv();
            if (env != null) {
              commandLine.setEnvParams(env);
              commandLine.setPassParentEnvs(javaParameters.isPassParentEnvs());
            }

            return commandLine;
          }
          catch (CantRunException e) {
            throw new RuntimeException(e);
          }
        }
      });
    }
    catch (RuntimeException e) {
      if(e.getCause() instanceof CantRunException)
        throw ((CantRunException)e.getCause());
      else
        throw e;
    }
  }

  private void setPassParentEnvs(final boolean passParentEnvs) {
    myPassParentEnvs = passParentEnvs;
  }

}
