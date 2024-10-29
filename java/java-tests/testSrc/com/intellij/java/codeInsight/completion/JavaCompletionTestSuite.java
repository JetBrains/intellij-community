// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.TestAll;
import com.intellij.TestCaseLoader;
import com.intellij.java.codeInsight.completion.ml.JavaCompletionFeaturesTest;
import com.intellij.java.codeInsight.template.postfix.templates.PostfixTemplateTestCase;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.TestIndexingModeSupporter;
import junit.framework.Test;
import junit.framework.TestSuite;

import java.util.List;

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
    TestCaseLoader myTestCaseLoader = TestCaseLoader.Builder.fromEmpty()
      .withTestGroupsResourcePath(TestCaseLoader.COMMON_TEST_GROUPS_RESOURCE_NAME)
      .withTestGroups(List.of("JAVA_TESTS"))
      .build();
    myTestCaseLoader.fillTestCases("", TestAll.getClassRoots());
    List<Class<?>> classes = myTestCaseLoader.getClasses();
    for (Class<?> aClass : classes) {
      if (!aClass.getSimpleName().contains("Completion") && 
          !PostfixTemplateTestCase.class.isAssignableFrom(aClass)) continue;
      // JavaCompletionFeaturesTest does not depend on indices
      if (JavaCompletionFeaturesTest.class.equals(aClass)) continue;
      // Exclude feature suggester tests
      if (aClass.getPackageName().equals("com.intellij.java.ifs")) continue;
      if (TestIndexingModeSupporter.class.isAssignableFrom(aClass)) {
        Class<? extends TestIndexingModeSupporter> testCaseClass = aClass.asSubclass(TestIndexingModeSupporter.class);
        TestIndexingModeSupporter.addAllTests(testCaseClass, suite);
      } else if (!JavaCompletionTestSuite.class.equals(aClass)) {
        suite.addTest(warning("Unexpected " + aClass + " in the " + suite.getClass().getName() + " suite: " +
                              "its name contains 'Completion' substring but it doesn't implement TestIndexingModeSupporter"));
      }
    }
    return suite;
  }
}
