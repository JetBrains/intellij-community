// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ether;

import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.incremental.storage.ProjectStamps;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsModuleRootModificationUtil;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;

public class CommonTest extends IncrementalTestCase {
  public CommonTest() {
    super("common");
  }

  public void testAnonymous() {
    doTest();
  }

  public void testChangeDefinitionToClass() {
    doTest();
  }

  public void testChangeDefinitionToClass2() {
    doTest();
  }

  public void testDeleteClass() {
    doTest();
  }

  public void testDeleteClass1() {
    doTest();
  }

  public void testDeleteClass2() {
    doTest();
  }

  public void testDeleteClassAfterCompileErrors() {
    setupInitialProject();

    doTestBuild(2);
  }

  public void testDontMarkDependentsAfterCompileErrors() {
    setupInitialProject();

    doTestBuild(2);
  }

  public void testDeleteClassPackageDoesntMatchRoot() {
    doTest();
  }

  public void testInner() {
    doTest();
  }

  public void testNoResourceDelete() {
    doTest();
  }

  public void testNoSecondFileCompile() {
    doTest();
  }

  public void testNoSecondFileCompile1() {
    doTest();
  }

  public void testDependencyUpdate() {
    doTest();
  }

  public void testClass2Interface1() {
    doTest();
  }

  public void testClass2Interface2() {
    doTest();
  }

  public void testClass2Interface3() {
    doTest();
  }

  public void testDeleteClass3() {
    doTest();
  }

  public void testDeleteClass4() {
    doTest();
  }

  public void testDeleteInnerClass() {
    doTest();
  }

  public void testDeleteInnerClass1() {
    doTest();
  }

  public void testAddClass() {
    doTest();
  }

  public void testAddDuplicateClass() {
    doTest();
  }

  public void testAddClassHidingImportedClass() {
    doTest();
  }

  public void testAddClassHidingImportedClass2() {
    doTest();
  }

  public void testMoveClassToDependentModule() {
    JpsModule moduleA = addModule("moduleA", "moduleA/src");
    JpsModule moduleB = addModule("moduleB", "moduleB/src");
    JpsModuleRootModificationUtil.addDependency(moduleB, moduleA);
    doTestBuild(1).assertSuccessful();
  }

  public void testMoveClassToDependentModuleWithSameOutput() {
    final JpsSdk<JpsDummyElement> sdk = getOrCreateJdk();
    final String commonOutput = getAbsolutePath("out");
    JpsModule moduleA = addModule("moduleA", new String[]{getAbsolutePath("moduleA/src")}, commonOutput, commonOutput, sdk);
    JpsModule moduleB = addModule("moduleB", new String[]{getAbsolutePath("moduleB/src")}, commonOutput, commonOutput, sdk);
    JpsModuleRootModificationUtil.addDependency(moduleB, moduleA);
    doTestBuild(1).assertSuccessful();
  }

  public void testMoveClassFromJavaFileToDependentModule() {
    JpsModule moduleA = addModule("moduleA", "moduleA/src");
    JpsModule moduleB = addModule("moduleB", "moduleB/src");
    JpsModuleRootModificationUtil.addDependency(moduleB, moduleA);
    doTestBuild(1).assertSuccessful();
  }

  public void testCompileDependenciesOnMovedClassesInFirstRound() {
    doTest().assertSuccessful();
  }

  public void testIntegrateOnSuperclassRemovedAndRestored() {
    setupInitialProject();

    doTestBuild(2);
  }

  public void testMoveToplevelClassToAnotherFile() {
    doTest();
  }

  public void testMoveClassToAnotherRoot() {
    doTest();
  }

  public void testIntegrateOnNonIncrementalMake() {
    PlatformTestUtil.withSystemProperty(Mappings.PROCESS_CONSTANTS_NON_INCREMENTAL_PROPERTY, String.valueOf(true), () -> doTest());
  }

  public void testNothingChanged() {
    if (!ProjectStamps.PORTABLE_CACHES) return;
    doTest();
  }

  // Disabled because now several classes with the same qName in different modules are not supporter
  //
  //public void testConflictingClasses() {
  //  JpsModule module1 = addModule("module1", "module1/src");
  //  JpsModule module2 = addModule("module2", "module2/src");
  //  JpsModuleRootModificationUtil.addDependency(module2, module1);
  //  doTestBuild(1).assertSuccessful();
  //}
}
