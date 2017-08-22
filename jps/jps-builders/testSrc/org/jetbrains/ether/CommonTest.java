/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.ether;

import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsModuleRootModificationUtil;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author: db
 * Date: 22.09.11
 */
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
    doTest();
  }
}