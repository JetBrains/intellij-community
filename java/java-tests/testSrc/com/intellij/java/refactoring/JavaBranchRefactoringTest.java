// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.java.refactoring.convertToInstanceMethod.ConvertToInstance8MethodTest;
import com.intellij.java.refactoring.convertToInstanceMethod.ConvertToInstanceMethodTest;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.roots.RenameModuleTest;
import com.intellij.uiDesigner.refactoring.MoveFileTest;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings("NewClassNamingConvention") // to run on TeamCity
public class JavaBranchRefactoringTest {

  public static Test suite() {
    TestSuite suite = new TestSuite();
    List<Class<? extends TestCase>> testsToWrap = Arrays.asList(
      RenameClassTest.class,
      RenameCollisionsTest.class,
      RenameDirectoryTest.class,
      RenameFieldMultiTest.class,
      RenameFieldTest.class,
      RenameLocalTest.class,
      RenameMethodMultiTest.class,
      RenameModuleTest.class,

      MoveClassTest.class,
      MultipleJdksMoveClassTest.class,
      MovePackageTest.class,
      MovePackageMultirootTest.class,

      MoveFileTest.class,

      MoveMembersTest.class,
      ConvertToInstanceMethodTest.class,
      ConvertToInstance8MethodTest.class,
      MakeMethodStaticTest.class
      );
    for (Class<? extends TestCase> testClass : testsToWrap) {
      suite.addTest(enableBranchRefactoringsInside(testClass));
    }
    return suite;
  }

  private static TestSuite enableBranchRefactoringsInside(Class<? extends TestCase> aClass) {
    return new TestSuite(aClass) {
      @Override
      public void runTest(Test test, TestResult result) {
        RegistryValue registryValue = Registry.get("run.refactorings.in.model.branch");
        registryValue.setValue(true);
        try {
          super.runTest(test, result);
        }
        finally {
          registryValue.setValue(false);
        }
      }
    };
  }
}
