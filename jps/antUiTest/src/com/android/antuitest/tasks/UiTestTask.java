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
import org.apache.tools.ant.types.Commandline.Argument;
import org.apache.tools.ant.types.EnumeratedAttribute;
import org.apache.tools.ant.types.Path;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Custom Ant task for running UI tests.
 *
 * <p>The main advantage over a classic JUnitTask is the ability to shard tests to run in separate JVMs. We have more control over how to
 * split test batches (based on package name, annotations present, etc.) and over how to invoke them (because of command line restrictions
 * on Windows, our tests need special bootstrapping).</p>
 */
public class UiTestTask extends Task {

  private static final String TEST_SUITE_CLASS_NAME = "com.android.tools.idea.tests.gui.GuiTestSuite";
  private static final String TEST_GROUP_CLASS_NAME = "com.android.tools.idea.tests.gui.framework.TestGroup";
  private static final String TEST_RUNNER_CLASS_NAME = "com.android.tools.idea.tests.gui.framework.GuiTestSuiteRunner";
  private static final String RUN_IN_ANNOTATION_NAME = "com.android.tools.idea.tests.gui.framework.RunIn";

  private String classpathFile;
  private List<String> testGroups = Collections.emptyList();
  private Path classpath;
  private final List<Argument> jvmArgs = new ArrayList<>();

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
   * Takes a comma-delimited list of TestGroup values for groups to run. If empty or unspecified, all are run.
   */
  public void setTestGroups(String testGroupsString) {
    this.testGroups = Arrays.stream(testGroupsString.split(",")).filter(s -> !s.isEmpty()).collect(Collectors.toList());
  }

  @Override
  public void execute() throws BuildException {
    try {
      Map<String, Boolean> testTaskMapping = computeTestTasks(testGroups);
      for (String testSpec : testTaskMapping.keySet()) {
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
        if (testTaskMapping.get(testSpec)) {
          // Single test execution
          task.createJvmarg().setValue("-Dbootstrap.testcase=" + testSpec);
        } else {
          // Pass the entire test group, and let the test runner sift through GuiTestSuite.
          task.createJvmarg().setValue("-Dbootstrap.testcase=" + TEST_SUITE_CLASS_NAME);
          task.createJvmarg().setValue("-Dui.test.groups=" + testSpec);
        }

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
        jUnitTest.setOutfile("TEST-" + testSpec);
        task.addTest(jUnitTest);

        log("Executing UI tests in " + testSpec);
        task.execute();
      }
    } catch (Exception ex) {
      log(ex.getMessage(), ex, Project.MSG_ERR);
      throw new BuildException(ex);
    }
  }

  // Prepare the mapping for splitting up test execution: a task can be either an entire test group or an individual test.
  private Map<String, Boolean> computeTestTasks(List<String> testGroups) throws Exception {
    ClassLoader classLoader = createRuntimeClassLoader();
    Class<?> testGroupClass = classLoader.loadClass(TEST_GROUP_CLASS_NAME);
    Object[] testGroupValues = (Object[])testGroupClass.getMethod("values").invoke(null);

    Object[] testClasses = (Object[]) classLoader.loadClass(TEST_RUNNER_CLASS_NAME)
      .getMethod("getGuiTestClasses", Class.class)
      .invoke(null, classLoader.loadClass(TEST_SUITE_CLASS_NAME));

    Class<? extends Annotation> runInClass = classLoader.loadClass(RUN_IN_ANNOTATION_NAME).asSubclass(Annotation.class);

    Map<String, Boolean> result = new HashMap<>();
    for (Object testGroup : testGroupValues) {
      if (testGroups.isEmpty() || testGroups.contains(testGroup.toString())) {
        if ((Boolean)testGroupClass.getMethod("isForked").invoke(testGroup)) {
          for (Object testClass : testClasses) {
            if (shouldRunIn((Class<?>)testClass, runInClass, testGroup.toString())) {
              result.put(((Class<?>)testClass).getCanonicalName(), true);
            }
          }
        } else {
          result.put(testGroup.toString(), false);
        }
      }
    }
    return result;
  }

  // Create a classloader based on the classpath contents from classpathFile.
  private ClassLoader createRuntimeClassLoader() throws Exception {
    Collection<URL> urls = new LinkedHashSet<>();
    File file = new File(classpathFile);
    try {
      try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
        while (reader.ready()) {
          urls.add(new File(reader.readLine()).toURI().toURL());
        }
      }
      return new URLClassLoader(urls.toArray(new URL[urls.size()]));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // Returns true if klass or any of its methods is annotated with @RunIn(testGroup).
  private static boolean shouldRunIn(Class<?> klass, Class<? extends Annotation> runInClass, String testGroup) throws Exception {
    Method value = runInClass.getMethod("value");
    Annotation ann = klass.getAnnotation(runInClass);
    if (ann != null && testGroup.equals(value.invoke(ann).toString())) {
      return true;
    }
    for (Method method : klass.getMethods()) {
      ann = method.getAnnotation(runInClass);
      if (ann != null && testGroup.equals(value.invoke(ann).toString())) {
        return true;
      }
    }
    return false;
  }
}
