// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.TestAll;
import com.intellij.TestCaseLoader;
import com.intellij.java.codeInsight.completion.ml.JavaCompletionFeaturesTest;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.TestIndexingModeSupporter;
import junit.framework.Test;
import junit.framework.TestSuite;

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
      // JavaCompletionFeaturesTest does not depend on indices
      if (JavaCompletionFeaturesTest.class.equals(aClass)) continue;
      if (TestIndexingModeSupporter.class.isAssignableFrom(aClass)) {
        //noinspection unchecked
        Class<? extends TestIndexingModeSupporter> testCaseClass = (Class<? extends TestIndexingModeSupporter>)aClass;
        TestIndexingModeSupporter.addTest(testCaseClass, new TestIndexingModeSupporter.FullIndexSuite(), suite);
        TestIndexingModeSupporter.addTest(testCaseClass, new TestIndexingModeSupporter.RuntimeOnlyIndexSuite(), suite);
        TestIndexingModeSupporter.addTest(testCaseClass, new TestIndexingModeSupporter.EmptyIndexSuite(), suite);
      } else if (!JavaCompletionTestSuite.class.equals(aClass)) {
        suite.addTest(warning("Unexpected " + aClass + " in " + suite.getClass()));
      }
    }
    return suite;
  }
}
