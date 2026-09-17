// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.tmh;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import junit.framework.TestCase;

import static org.junit.Assert.assertThrows;

public abstract class TMHInstrumenterTestBase extends TestCase {

  private static final String TEST_DATA_PATH_PROPERTY = "jvm-inc-builder.tmh.test-data";
  private static final String TESTING_BACKGROUND_THREAD_NAME = "TESTING_BACKGROUND_THREAD";

  private final String dependencyPath;
  private final boolean useThreadingAssertions;

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  protected TMHInstrumenterTestBase(String dependencyPath, boolean useThreadingAssertions) {
    this.dependencyPath = dependencyPath;
    this.useThreadingAssertions = useThreadingAssertions;
  }

  final void doEdtTest() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    invokeMethod(testClass.aClass);
    assertThrows("Access is allowed from Event Dispatch Thread (EDT) only", Throwable.class, () -> executeInBackground(() -> invokeMethod(testClass.aClass)));
  }

  final TestClass getInstrumentedTestClass() throws IOException {
    TestClass testClass = prepareTest(false);
    assertTrue(testClass.isInstrumented);
    return testClass;
  }

  final TestClass getNotInstrumentedTestClass() throws IOException {
    TestClass testClass = prepareTest(false);
    assertFalse(testClass.isInstrumented);
    return testClass;
  }

  final TestClass prepareTest(@SuppressWarnings("SameParameterValue") boolean printClassFiles) throws IOException {
    List<File> classFiles = compileTestFiles();
    MyClassLoader classLoader = new MyClassLoader(getClass().getClassLoader());
    TestClass testClass = null;
    classFiles.sort(Comparator.comparing(File::getName));
    for (File classFile : classFiles) {
      String className = classFile.getName().replaceFirst("\\.class$", "");
      byte[] classData = Files.readAllBytes(classFile.toPath());
      if (!className.equals(getTestDataFileName())) {
        classLoader.doDefineClass(null, classData);
      }
      else {
        byte[] instrumentedClassData = TMHTestUtil.instrument(classData, useThreadingAssertions);
        if (instrumentedClassData != null) {
          testClass = new TestClass(classLoader.doDefineClass(null, instrumentedClassData), instrumentedClassData, true);
          if (printClassFiles) {
            TMHTestUtil.printDebugInfo(classData, instrumentedClassData);
          }
        }
        else {
          testClass = new TestClass(classLoader.doDefineClass(null, classData), classData, false);
        }
      }
    }
    assertNotNull("Class " + getTestDataFileName() + " not found!", testClass);
    return testClass;
  }

  private List<File> compileTestFiles() throws IOException {
    File testFile = new File(getTestDataPath(), getTestDataFileName() + ".java");
    File classesDir = Files.createTempDirectory("tmh-test-output").toFile();

    File dependenciesDir = new File(getTestDataPath(), dependencyPath);
    File[] dependencies = dependenciesDir.listFiles();
    if (dependencies == null || dependencies.length == 0) {
      throw new IllegalStateException("Cannot find dependencies at " + dependenciesDir.getAbsolutePath());
    }
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull("The test needs a JDK compiler", compiler);
    List<File> sourceFiles = new ArrayList<>(List.of(dependencies));
    sourceFiles.add(testFile);
    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
      fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classesDir.toPath()));
      assertTrue("Cannot compile test sources", compiler.getTask(null, fileManager, null, null, null,
                                                                 fileManager.getJavaFileObjectsFromFiles(sourceFiles)).call());
    }
    try (Stream<Path> walk = Files.walk(Paths.get(classesDir.getAbsolutePath()))) {
      return walk
        .filter(Files::isRegularFile)
        .map(Path::toFile)
        .collect(Collectors.toList());
    }
  }

  private static File getTestDataPath() {
    String testDataFile = System.getProperty(TEST_DATA_PATH_PROPERTY);
    if (testDataFile == null) {
      throw new IllegalStateException("The test data path is not set");
    }
    File testDataRoot = new File(testDataFile).getParentFile();
    if (testDataRoot == null) {
      throw new IllegalStateException("The test data file has no parent directory");
    }
    return testDataRoot;
  }

  private String getTestDataFileName() {
    String testName = getName();
    return testName.startsWith("test") ? testName.substring("test".length()) : testName;
  }

  static void invokeMethod(Class<?> testClass) {
    rethrowExceptions(() -> {
      Object instance = testClass.getDeclaredConstructor().newInstance();
      Method method = testClass.getMethod("test");
      method.invoke(instance);
    });
  }

  private static void rethrowExceptions(ThrowingRunnable runnable) {
    try {
      runnable.run();
    }
    catch (Throwable e) {
      //noinspection InstanceofCatchParameter
      if (e instanceof InvocationTargetException) {
        throwUnchecked(e.getCause());
      }
      throwUnchecked(e);
    }
  }

  static void executeInBackground(ThrowingRunnable runnable) throws ExecutionException {
    ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, TESTING_BACKGROUND_THREAD_NAME));
    try {
      waitResult(executor.submit(() -> rethrowExceptions(runnable)));
    }
    finally {
      executor.shutdownNow();
    }
  }

  private static void waitResult(Future<?> future) throws ExecutionException {
    try {
      future.get(10, TimeUnit.MINUTES);
    }
    catch (InterruptedException | TimeoutException e) {
      e.printStackTrace();
      fail("Background computation didn't finish as expected");
    }
  }

  private static void throwUnchecked(Throwable throwable) {
    TMHInstrumenterTestBase.<RuntimeException>throwAs(throwable);
  }

  @SuppressWarnings("unchecked")
  private static <T extends Throwable> void throwAs(Throwable throwable) throws T {
    throw (T)throwable;
  }

  @FunctionalInterface
  interface ThrowingRunnable {
    void run() throws Throwable;
  }

  private static class MyClassLoader extends ClassLoader {
    MyClassLoader(ClassLoader parent) {
      super(parent);
    }

    public Class<?> doDefineClass(String name, byte[] data) {
      return defineClass(name, data, 0, data.length);
    }
  }

  static class TestClass {
    final Class<?> aClass;
    final byte[] classBytes;
    final boolean isInstrumented;

    TestClass(Class<?> aClass, byte[] classBytes, boolean isInstrumented) {
      this.aClass = aClass;
      this.classBytes = classBytes;
      this.isInstrumented = isInstrumented;
    }
  }
}
