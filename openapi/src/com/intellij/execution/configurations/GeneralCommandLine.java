/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.execution.configurations;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.projectRoots.ProjectJdk;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class GeneralCommandLine {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.configurations.GeneralCommandLine");
  private HashMap<String, String> myEnvParams;
  private String myExePath = null;
  private File myWorkDirectory = null;
  private ParametersList myProgramParams = new ParametersList();
  private Charset myCharset = CharsetToolkit.getDefaultSystemCharset();

  public void setExePath(final String exePath) {
    myExePath = quote(exePath.trim());
  }

  public void setWorkDirectory(final String path) {
    setWorkingDirectory(path != null? new File(path) : null);
  }

  public void setWorkingDirectory(final File workingDirectory) {
    myWorkDirectory = workingDirectory;
  }

  public void setEnvParams(final HashMap<String, String> envParams) {
    myEnvParams = envParams;
  }

  public void setCharset(final Charset charset) {
    if (charset == null) {
      LOG.error("null charset");
      return;
    }
    myCharset = charset;
    addParameter("-Dfile.encoding=" + charset.name());
  }

  public void addParameters(final String[] parameters) {
    for (int i = 0; i < parameters.length; i++) {
      addParameter(parameters[i]);
    }
  }

  public void addParameters(final List<String> parameters) {
    for (Iterator<String> it = parameters.iterator(); it.hasNext();) {
      addParameter(it.next());
    }
  }

  public void addParameter(final String parameter) {
    LOG.assertTrue(parameter != null);
    myProgramParams.add(quote(parameter));
  }

  public String getCommandLineString() {
    final StringBuffer buffer = new StringBuffer(myExePath);
    for (Iterator<String> iterator = myProgramParams.getList().iterator(); iterator.hasNext();) {
      buffer.append(" ");
      buffer.append(iterator.next());
    }
    return buffer.toString();
  }

  public Charset getCharset() {
    return myCharset;
  }

  public Process createProcess() throws ExecutionException {
    checkWorkingDirectory();
    try {
      final String[] commands = getCommands();
      if(commands[0] == null) throw new CantRunException("Executable is not specified");
      
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
      throw new ExecutionException("Cannot start process, the working directory " + myWorkDirectory.getAbsolutePath() + " does not exist");
    }
    if (!myWorkDirectory.isDirectory()) {
      throw new ExecutionException("Cannot start process, the path specified for working directory is not a directory");
    }
  }

  private String[] getEnvParamsArray() {
    if (myEnvParams == null) {
      return null;
    }
    final String[] result = new String[myEnvParams.size()];
    int i=0;
    for (Iterator<String> it = myEnvParams.keySet().iterator(); it.hasNext();) {
      final String key = it.next();
      result[i++] = key + "=" + myEnvParams.get(key).trim();
    }
    return result;
  }

  public String[] getCommands() {
    final List<String> parameters = myProgramParams.getList();
    final String[] result = new String[parameters.size() + 1];
    result[0] = myExePath;
    int index = 1;
    for (Iterator<String> iterator = parameters.iterator(); iterator.hasNext(); index++) {
      final String param = iterator.next();
      result[index] = param;
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
    clone.myEnvParams = myEnvParams != null ? (HashMap<String, String>)myEnvParams.clone() : null;
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
              throw new CantRunException("No JDK specified");
            }

            final String exePath = jdk.getVMExecutablePath();
            if(exePath == null) {
              throw new CantRunException("Cannot find VM executable");
            }
            commandLine.setExePath(exePath);
            commandLine.addParameters(javaParameters.getVMParametersList().getList());
            Charset charset = javaParameters.getCharset();
            if (charset == null) charset = CharsetToolkit.getIDEOptionsCharset();
            if (charset == null) charset = CharsetToolkit.getDefaultSystemCharset();
            commandLine.setCharset(charset);

            if(!javaParameters.getVMParametersList().hasParameter("-classpath") && !javaParameters.getVMParametersList().hasParameter("-cp")){
              commandLine.addParameter("-classpath");
              commandLine.addParameter(javaParameters.getClassPath().getPathsString());
            }

            String mainClass = javaParameters.getMainClass();
            if(mainClass == null) throw new CantRunException("Main class is not specified");
            commandLine.addParameter(mainClass);
            commandLine.addParameters(javaParameters.getProgramParametersList().getList());
            commandLine.setWorkDirectory(javaParameters.getWorkingDirectory());

            final HashMap<String, String> env = javaParameters.getEnv();
            if (env != null) {
              commandLine.setEnvParams(env);
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

}
