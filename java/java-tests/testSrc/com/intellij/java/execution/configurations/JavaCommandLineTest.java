// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    assertFalse(containsClassPath(commandLineString));

    javaParameters = new JavaParameters();
    javaParameters.setJdk(internalJdk);
    javaParameters.getClassPath().add("my-jar-file.jar");
    javaParameters.setMainClass("Main");
    javaParameters.getVMParametersList().add("-classpath");
    javaParameters.getVMParametersList().add("..");
    commandLineString = javaParameters.toCommandLine().getCommandLineString();
    commandLineString = removeClassPath(commandLineString, "-classpath ..");
    assertFalse(containsClassPath(commandLineString));
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