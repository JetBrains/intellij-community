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

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.testFramework.LightIdeaTestCase;
import junit.framework.Assert;

public class JavaCommandLineTest extends LightIdeaTestCase {
  public void testJdk() {
    try {
      CommandLineBuilder.createFromJavaParameters(new JavaParameters());
      fail("CantRunException (main class is not specified) expected");
    }
    catch (CantRunException e) {
      Assert.assertEquals(ExecutionBundle.message("run.configuration.error.no.jdk.specified"), e.getMessage());
    }
  }

  public void testMainClass() {
    try {
      JavaParameters javaParameters = new JavaParameters();
      javaParameters.setJdk(getProjectJDK());
      CommandLineBuilder.createFromJavaParameters(javaParameters);
      fail("CantRunException (main class is not specified) expected");
    }
    catch (CantRunException e) {
      assertEquals(ExecutionBundle.message("main.class.is.not.specified.error.message"), e.getMessage());
    }
  }

  public void testJarParameter() throws CantRunException {
    JavaParameters javaParameters = new JavaParameters();
    javaParameters.setJdk(getProjectJDK());
    javaParameters.setJarPath("my-jar-file.jar");
    String commandLineString = CommandLineBuilder.createFromJavaParameters(javaParameters).getCommandLineString();
    assertTrue(commandLineString, commandLineString.contains("-jar my-jar-file.jar"));
  }

  public void testClasspath() throws CantRunException {
    JavaParameters javaParameters;
    String commandLineString;

    javaParameters = new JavaParameters();
    final Sdk internalJdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    javaParameters.setJdk(internalJdk);
    javaParameters.getClassPath().add("my-jar-file.jar");
    javaParameters.setMainClass("Main");
    commandLineString = CommandLineBuilder.createFromJavaParameters(javaParameters).getCommandLineString();
    assertTrue(containsClassPath(commandLineString));

    javaParameters = new JavaParameters();
    javaParameters.setJdk(internalJdk);
    javaParameters.getClassPath().add("my-jar-file.jar");
    javaParameters.setMainClass("Main");
    javaParameters.getVMParametersList().add("-cp");
    javaParameters.getVMParametersList().add("..");
    commandLineString = CommandLineBuilder.createFromJavaParameters(javaParameters).getCommandLineString();
    commandLineString = removeClassPath(commandLineString, "-cp ..");
    assertTrue(!containsClassPath(commandLineString));

    javaParameters = new JavaParameters();
    javaParameters.setJdk(internalJdk);
    javaParameters.getClassPath().add("my-jar-file.jar");
    javaParameters.setMainClass("Main");
    javaParameters.getVMParametersList().add("-classpath");
    javaParameters.getVMParametersList().add("..");
    commandLineString = CommandLineBuilder.createFromJavaParameters(javaParameters).getCommandLineString();
    commandLineString = removeClassPath(commandLineString, "-classpath ..");
    assertTrue(!containsClassPath(commandLineString));
  }

  private static boolean containsClassPath(String commandLineString) {
    return commandLineString.contains("-cp") || commandLineString.contains("-classpath");
  }

  private static String removeClassPath(String commandLineString, String pathString) {
    int i = commandLineString.indexOf(pathString);
    commandLineString = commandLineString.substring(0, i) + commandLineString.substring(i + pathString.length());
    return commandLineString;
  }

  public void testCreateProcess() {
    try {
      new KillableColoredProcessHandler(new GeneralCommandLine());
      fail("ExecutionException (executable is not specified) expected");
    }
    catch (ExecutionException e) {
      assertEquals(IdeBundle.message("run.configuration.error.executable.not.specified"), e.getMessage());
    }
  }
}
