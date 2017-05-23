/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.execution.configurations;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import org.junit.Test;

import static org.junit.Assert.*;

public class JavaCommandLineTest extends BareTestFixtureTestCase {
  @Test
  public void testJdkMissing() {
    try {
      new JavaParameters().toCommandLine();
      fail("'JDK missing' expected");
    }
    catch (CantRunException e) {
      assertEquals(ExecutionBundle.message("run.configuration.error.no.jdk.specified"), e.getMessage());
    }
  }

  @Test
  public void testMainClassMissing() {
    try {
      JavaParameters javaParameters = new JavaParameters();
      javaParameters.setJdk(getProjectJDK());
      javaParameters.toCommandLine();
      fail("'main class missing' expected");
    }
    catch (CantRunException e) {
      assertEquals(ExecutionBundle.message("main.class.is.not.specified.error.message"), e.getMessage());
    }
  }

  @Test
  public void testJarParameter() throws CantRunException {
    JavaParameters javaParameters = new JavaParameters();
    javaParameters.setJdk(getProjectJDK());
    javaParameters.setJarPath("my-jar-file.jar");
    String commandLineString = javaParameters.toCommandLine().getCommandLineString();
    assertTrue(commandLineString, commandLineString.contains("-jar my-jar-file.jar"));
  }

  @Test
  public void testClasspath() throws CantRunException {
    JavaParameters javaParameters;
    String commandLineString;

    javaParameters = new JavaParameters();
    Sdk internalJdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    javaParameters.setJdk(internalJdk);
    javaParameters.getClassPath().add("my-jar-file.jar");
    javaParameters.setMainClass("Main");
    commandLineString = javaParameters.toCommandLine().getCommandLineString();
    assertTrue(containsClassPath(commandLineString));

    javaParameters = new JavaParameters();
    javaParameters.setJdk(internalJdk);
    javaParameters.getClassPath().add("my-jar-file.jar");
    javaParameters.setMainClass("Main");
    javaParameters.getVMParametersList().add("-cp");
    javaParameters.getVMParametersList().add("..");
    commandLineString = javaParameters.toCommandLine().getCommandLineString();
    commandLineString = removeClassPath(commandLineString, "-cp ..");
    assertTrue(!containsClassPath(commandLineString));

    javaParameters = new JavaParameters();
    javaParameters.setJdk(internalJdk);
    javaParameters.getClassPath().add("my-jar-file.jar");
    javaParameters.setMainClass("Main");
    javaParameters.getVMParametersList().add("-classpath");
    javaParameters.getVMParametersList().add("..");
    commandLineString = javaParameters.toCommandLine().getCommandLineString();
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

  @Test
  public void testCreateProcess() {
    try {
      new KillableColoredProcessHandler(new GeneralCommandLine());
      fail("'executable missing' expected");
    }
    catch (ExecutionException e) {
      assertEquals(IdeBundle.message("run.configuration.error.executable.not.specified"), e.getMessage());
    }
  }

  private static Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk17();
  }
}