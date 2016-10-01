/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.antuitest.tasks;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.optional.junit.FormatterElement;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTask;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;
import org.apache.tools.ant.types.*;
import org.apache.tools.ant.types.Commandline.Argument;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * Custom Ant task for running UI tests.
 *
 * <p>The main advantage over a classic JUnitTask is the ability to shard tests to run in separate JVMs. We have more control over how to
 * split test batches (based on package name, annotations present, etc.) and over how to invoke them (because of command line restrictions
 * on Windows, our tests need special bootstrapping).</p>
 */
public class UiTestTask extends Task {

  private String classpathFile;
  private String testSuite;
  private Path classpath;
  private final List<Argument> jvmArgs = new ArrayList<Argument>();

  public UiTestTask() throws Exception {
  }

  /**
   * Sets file containing actual test classpath.
   */
  public void setClasspathFile(String classpathFile) {
    this.classpathFile = classpathFile;
  }

  /**
   * Allows nested classpath elements, similar to JUnitTask.
   *
   * <p>Use this for the bootstrapping classpath, as this is added to the command line and can be huge!</p>
   */
  public Path createClasspath() {
    if (classpath == null) {
      classpath = new Path(getProject()).createPath();
    }
    return classpath;
  }

  /**
   * Allows nested jvmarg elements, similar to JUnitTask.
   */
  public Argument createJvmarg() {
    Argument jvmArg = new Argument();
    jvmArgs.add(jvmArg);
    return jvmArg;
  }

  /**
   * Specifies the canonical class name of the test suite, a class annotated with @RunWith(GuiTestSuiteRunner.class).
   */
  public void setTestSuite(String testSuite) {
    this.testSuite = testSuite;
  }

  @Override
  public void execute() throws BuildException {
    long start = System.currentTimeMillis();
    Map<String, List<Class<?>>> testGroups = getTestGroups();
    log("Classpath scanning for test classes took " + (System.currentTimeMillis() - start) + " ms");

    try {
      for (String testGroup : testGroups.keySet()) {
        JUnitTask task = new JUnitTask();
        task.init();
        task.setProject(getProject());
        task.setTaskName("uitest");

        task.setFork(true);
        task.setForkMode(new JUnitTask.ForkMode("once"));

        task.setLogFailedTests(true);
        task.setShowOutput(true);
        task.setPrintsummary((JUnitTask.SummaryAttribute) EnumeratedAttribute.getInstance(JUnitTask.SummaryAttribute.class, "true"));

        task.createJvmarg().setValue("-Dclasspath.file=" + classpathFile);
        task.createJvmarg().setValue("-Dbootstrap.testcase=" + getTestSpec(testGroups.get(testGroup)));

        Path testClasspath = task.createClasspath();
        testClasspath.add(classpath);

        for (Argument jvmArg : jvmArgs) {
          task.createJvmarg().setValue(jvmArg.getParts()[0]);
        }

        FormatterElement plainFormatter = new FormatterElement();
        plainFormatter.setType(
          (FormatterElement.TypeAttribute) EnumeratedAttribute.getInstance(FormatterElement.TypeAttribute.class, "plain"));
        task.addFormatter(plainFormatter);

        FormatterElement xmlFormatter = new FormatterElement();
        xmlFormatter.setType(
          (FormatterElement.TypeAttribute) EnumeratedAttribute.getInstance(FormatterElement.TypeAttribute.class, "xml"));
        task.addFormatter(xmlFormatter);

        JUnitTest jUnitTest = new JUnitTest("com.intellij.tests.BootstrapUITests");
        // Make sure we set a different outfile for each invocation, to avoid test reports clobbering each other. Previously, all
        // invocations would write to TEST-com.intellij.tests.BootstrapUITests.txt.
        jUnitTest.setOutfile("TEST-" + testGroup);
        task.addTest(jUnitTest);

        log("Executing UI tests in " + testGroup);
        task.execute();
      }
    } catch (Exception ex) {
      log(ex.getMessage(), ex, Project.MSG_ERR);
      throw new BuildException(ex);
    }
  }

  /**
   * Decides how to shard the test classes based on their TestGroup annotations.
   *
   * @return a map from test group name to list of test classes.
   */
  public Map<String, List<Class<?>>> getTestGroups() {
    Map<String, List<Class<?>>> result = new HashMap<String, List<Class<?>>>();

    try {
      ClassLoader classLoader = createRuntimeClassLoader();
      Class<?> testSuiteRunnerClass = classLoader.loadClass("com.android.tools.idea.tests.gui.framework.GuiTestSuiteRunner");
      Method getGuiTestClasses = testSuiteRunnerClass.getMethod("getGuiTestClasses", Class.class);
      Method getTestGroup = testSuiteRunnerClass.getMethod("getTestGroup", Class.class);

      Class<?>[] testClasses = (Class<?>[]) getGuiTestClasses.invoke(null, classLoader.loadClass(testSuite));
      for (Class<?> testClass : testClasses) {
        Object testGroup = getTestGroup.invoke(null, testClass);
        addToTestGroup(result, testGroup.toString(), testClass);
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return result;
  }

  private static boolean addToTestGroup(Map<String, List<Class<?>>> testGroups, String groupName, Class<?> testClass) {
    if (!testGroups.containsKey(groupName)) {
      testGroups.put(groupName, new ArrayList<Class<?>>());
    }
    return testGroups.get(groupName).add(testClass);
  }

  // Create a classloader based on the classpath contents from classpathFile.
  private ClassLoader createRuntimeClassLoader() throws Exception {
    Collection<URL> urls = new LinkedHashSet<URL>();
    File file = new File(classpathFile);
    try {
      final BufferedReader reader = new BufferedReader(new FileReader(file));
      try {
        while (reader.ready()) {
          urls.add(new File(reader.readLine()).toURI().toURL());
        }
      }
      finally {
        reader.close();
      }
      return new URLClassLoader(urls.toArray(new URL[urls.size()]));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a comma-separated list of test class names, to be passed via -Dbootstrap.testcase to BootstrapUITests.
   */
  public String getTestSpec(List<Class<?>> classes) {
    StringBuilder sb = new StringBuilder();
    for (Class<?> clazz : classes) {
      sb.append(clazz.getCanonicalName());
      sb.append(",");
    }
    return sb.toString();
  }
}
