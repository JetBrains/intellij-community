// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion;

import com.intellij.TestAll;
import com.intellij.TestCaseLoader;
import com.intellij.codeInsight.completion.JavaCompletionAutoPopupTestCase;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.TestIndexingModeSupporter;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * To run a separate test from this suite with needed IndexingMode in an IDE, comment in {@link #suite()}
 * <pre>
 * if (!"Java Dumb Completion Tests".equals(System.getProperty("teamcity.buildConfName"))) {
 *    return suite;
 * }
 * </pre>
 * and make test's {@code getIndexingMode} return it, and run test from IDE as usual.
 * Also, one can replace {@code System.setProperty("intellij.build.test.groups", "JAVA_TESTS");} in {@link #suite()}
 * with {@code System.setProperty("intellij.build.test.patterns", "<needed package>");} and run just this package.
 */
@SkipSlowTestLocally
public class JavaCompletionTestSuite extends TestSuite {

  private static final TestIndexingModeSupporter.IndexingModeTestHandler FULL_INDEX_TRANSFORMATION = new FullIndexSuite();

  private static final class FullIndexSuite extends TestIndexingModeSupporter.IndexingModeTestHandler {
    private FullIndexSuite() {
      super("Full index", "Full index: ");
    }

    @Override
    public boolean shouldIgnore(@NotNull Class<? extends TestIndexingModeSupporter> aClass) {
      return JavaCompletionAutoPopupTestCase.class.isAssignableFrom(aClass) ||
             CompletionHintsTest.class == aClass ||
             aClass.isAnnotationPresent(NeedsIndex.SmartMode.class);
    }

    @Override
    public boolean shouldIgnore(@NotNull Method method,
                                @NotNull Class<? extends TestIndexingModeSupporter> aClass) {
      return method.isAnnotationPresent(NeedsIndex.SmartMode.class);
    }

    @Override
    public TestIndexingModeSupporter.@NotNull IndexingMode getIndexingMode() {
      return TestIndexingModeSupporter.IndexingMode.DUMB_FULL_INDEX;
    }
  }

  private static final TestIndexingModeSupporter.IndexingModeTestHandler RUNTIME_ONLY_INDEX_TRANSFORMATION = new RuntimeOnlyIndexSuite();

  private static final class RuntimeOnlyIndexSuite extends TestIndexingModeSupporter.IndexingModeTestHandler {

    private RuntimeOnlyIndexSuite() {
      super("RuntimeOnlyIndex", "Runtime only index: ");
    }

    @Override
    public boolean shouldIgnore(@NotNull Class<? extends TestIndexingModeSupporter> aClass) {
      return FULL_INDEX_TRANSFORMATION.shouldIgnore(aClass) || aClass.isAnnotationPresent(NeedsIndex.Full.class);
    }

    @Override
    public boolean shouldIgnore(@NotNull Method method,
                                @NotNull Class<? extends TestIndexingModeSupporter> aClass) {
      return FULL_INDEX_TRANSFORMATION.shouldIgnore(method, aClass) || method.isAnnotationPresent(NeedsIndex.Full.class);
    }

    @Override
    public TestIndexingModeSupporter.@NotNull IndexingMode getIndexingMode() {
      return TestIndexingModeSupporter.IndexingMode.DUMB_RUNTIME_ONLY_INDEX;
    }
  }

  private static final TestIndexingModeSupporter.IndexingModeTestHandler EMPTY_INDEX_TRANSFORMATION = new EmptyIndexSuite();

  private static final class EmptyIndexSuite extends TestIndexingModeSupporter.IndexingModeTestHandler {

    private EmptyIndexSuite() {
      super("EmptyIndex", "Empty index: ");
    }

    @Override
    public boolean shouldIgnore(@NotNull Class<? extends TestIndexingModeSupporter> aClass) {
      return RUNTIME_ONLY_INDEX_TRANSFORMATION.shouldIgnore(aClass) || aClass.isAnnotationPresent(NeedsIndex.ForStandardLibrary.class);
    }

    @Override
    public boolean shouldIgnore(@NotNull Method method, @NotNull Class<? extends TestIndexingModeSupporter> aClass) {
      return RUNTIME_ONLY_INDEX_TRANSFORMATION.shouldIgnore(method, aClass) || method.isAnnotationPresent(NeedsIndex.ForStandardLibrary.class);
    }

    @Override
    public TestIndexingModeSupporter.@NotNull IndexingMode getIndexingMode() {
      return TestIndexingModeSupporter.IndexingMode.DUMB_EMPTY_INDEX;
    }
  }

  public static Test suite() {
    JavaCompletionTestSuite suite = new JavaCompletionTestSuite();
    if (!"Java Dumb Completion Tests".equals(System.getProperty("teamcity.buildConfName"))) {
      return suite;
    }
    suite.setName("Java completion tests suite");
    System.setProperty("intellij.build.test.groups", "JAVA_TESTS");
    TestCaseLoader myTestCaseLoader = new TestCaseLoader("tests/testGroups.properties");
    myTestCaseLoader.fillTestCases("", TestAll.getClassRoots());
    for (Class<?> aClass : myTestCaseLoader.getClasses()) {
      if (!aClass.getSimpleName().contains("Completion")) continue;
      if (TestIndexingModeSupporter.class.isAssignableFrom(aClass)) {
        //noinspection unchecked
        Class<? extends TestIndexingModeSupporter> testCaseClass = (Class<? extends TestIndexingModeSupporter>)aClass;
        TestIndexingModeSupporter.addTest(testCaseClass, FULL_INDEX_TRANSFORMATION, suite);
        TestIndexingModeSupporter.addTest(testCaseClass, RUNTIME_ONLY_INDEX_TRANSFORMATION, suite);
        TestIndexingModeSupporter.addTest(testCaseClass, EMPTY_INDEX_TRANSFORMATION, suite);
      } else if (!JavaCompletionTestSuite.class.equals(aClass)) {
        suite.addTest(warning("Unexpected class " + aClass + " in " + suite.getClass()));
      }
    }
    return suite;
  }
}
