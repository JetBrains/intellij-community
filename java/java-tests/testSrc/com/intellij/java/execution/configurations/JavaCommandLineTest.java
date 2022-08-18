// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution.configurations;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.util.io.IdeUtilIoBundle;
import com.intellij.util.lang.JavaVersion;
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

  @Test
  public void testWithStdEncoding() throws CantRunException {
    JavaParameters parameters = new JavaParameters();
    parameters.setMainClass("FakeMain");
    parameters.setJdk(IdeaTestUtil.getMockJdk(JavaVersion.compose(18)));
    assertTrue(parameters.toCommandLine().getCommandLineString().contains("-Dsun.stdout.encoding="));
    
    parameters.setJdk(IdeaTestUtil.getMockJdk17());
    assertFalse(parameters.toCommandLine().getCommandLineString().contains("-Dsun.stdout.encoding="));
    
    parameters.setJdk(IdeaTestUtil.getMockJdk(JavaVersion.compose(18)));
    String consoleEncoding = "-Dsun.stdout.encoding=UTF-8";
    parameters.getVMParametersList().add(consoleEncoding);
    String commandLineString = parameters.toCommandLine().getCommandLineString();
    int i = commandLineString.indexOf(consoleEncoding);
    assertTrue(i > 0);
    assertFalse(commandLineString.indexOf(consoleEncoding, i + consoleEncoding.length()) > 0);
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
      assertEquals(IdeUtilIoBundle.message("run.configuration.error.executable.not.specified"), e.getMessage());
    }
  }

  private static Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk17();
  }
}