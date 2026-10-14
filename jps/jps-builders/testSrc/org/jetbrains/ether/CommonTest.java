// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ether;

import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsModuleRootModificationUtil;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class CommonTest extends IncrementalTestCase {
  private static final Set<String> GRAPH_ONLY_TESTS = Set.of(
    "addClassHidingImportedClass", "addClassHidingImportedClass2", "deletePermittedClass", "deleteSealedPermission", "moveInnerClassToTopLevel"
  );

  public CommonTest() {
    super("common");
  }
  @Override
  protected boolean shouldRunTest() {
    if (JavaBuilderUtil.isDepGraphEnabled()) {
      return super.shouldRunTest();
    }
    return !GRAPH_ONLY_TESTS.contains(getTestName(true));
  }

  @Test
  public void testAnonymous() {
    doTest();
  }

  @Test
  public void testChangeDefinitionToClass() {
    doTest();
  }

  @Test
  public void testChangeDefinitionToClass2() {
    doTest();
  }

  @Test
  public void testDeleteClass() {
    doTest();
  }

  @Test
  public void testDeleteClass1() {
    doTest();
  }

  @Test
  public void testDeleteClass2() {
    doTest();
  }

  @Test
  public void testDeleteClassAfterCompileErrors() {
    setupInitialProject();

    doTestBuild(2);
  }

  @Test
  public void testDontMarkDependentsAfterCompileErrors() {
    setupInitialProject();

    doTestBuild(2);
  }

  @Test
  public void testDeleteClassPackageDoesntMatchRoot() {
    doTest();
  }

  @Test
  public void testDeleteImportedClass() {
    doTest().assertFailed();
  }

  @Test
  public void testInner() {
    doTest();
  }

  @Test
  public void testMoveInnerClassToTopLevel() {
    doTest();
  }

  @Test
  public void testNoResourceDelete() {
    doTest();
  }

  @Test
  public void testNoSecondFileCompile() {
    doTest();
  }

  @Test
  public void testNoSecondFileCompile1() {
    doTest();
  }

  @Test
  public void testDependencyUpdate() {
    doTest();
  }

  @Test
  public void testClass2Interface1() {
    doTest();
  }

  @Test
  public void testClass2Interface2() {
    doTest();
  }

  @Test
  public void testClass2Interface3() {
    doTest();
  }

  @Test
  public void testDeleteClass3() {
    doTest();
  }

  @Test
  public void testDeleteClass4() {
    doTest();
  }

  @Test
  public void testDeletePermittedClass() {
    doTest().assertFailed();
  }

  @Test
  public void testDeleteSealedPermission() {
    doTest().assertFailed();
  }

  @Test
  public void testDeleteInnerClass() {
    doTest();
  }

  @Test
  public void testDeleteInnerClass1() {
    doTest();
  }

  @Test
  public void testAddClass() {
    doTest();
  }

  @Test
  public void testAddDuplicateClass() {
    doTest();
  }

  @Test
  public void testAddClassHidingImportedClass() {
    doTest();
  }

  @Test
  public void testAddClassHidingImportedClass2() {
    doTest();
  }

  @Test
  public void testMoveClassToDependentModule() {
    JpsModule moduleA = addModule("moduleA", "moduleA/src");
    JpsModule moduleB = addModule("moduleB", "moduleB/src");
    JpsModuleRootModificationUtil.addDependency(moduleB, moduleA);
    doTestBuild(1).assertSuccessful();
  }

  @Test
  public void testMoveClassToDependentModuleWithSameOutput() {
    final JpsSdk<JpsDummyElement> sdk = getOrCreateJdk();
    final String commonOutput = getAbsolutePath("out");
    JpsModule moduleA = addModule("moduleA", new String[]{getAbsolutePath("moduleA/src")}, commonOutput, commonOutput, sdk);
    JpsModule moduleB = addModule("moduleB", new String[]{getAbsolutePath("moduleB/src")}, commonOutput, commonOutput, sdk);
    JpsModuleRootModificationUtil.addDependency(moduleB, moduleA);
    doTestBuild(1).assertSuccessful();
  }

  @Test
  public void testMoveClassFromJavaFileToDependentModule() {
    JpsModule moduleA = addModule("moduleA", "moduleA/src");
    JpsModule moduleB = addModule("moduleB", "moduleB/src");
    JpsModuleRootModificationUtil.addDependency(moduleB, moduleA);
    doTestBuild(1).assertSuccessful();
  }

  @Test
  public void testSameClassesInDifferentModules() {
    JpsModule moduleA = addModule("moduleA", "moduleA/src");
    JpsModule moduleB = addModule("moduleB", "moduleB/src");
    JpsModuleRootModificationUtil.addDependency(moduleB, moduleA); // ensure compilation sequence
    doTestBuild(1).assertSuccessful();
  }

  @Test
  public void testCompileDependenciesOnMovedClassesInFirstRound() {
    doTest().assertSuccessful();
  }

  @Test
  public void testIntegrateOnSuperclassRemovedAndRestored() {
    setupInitialProject();

    doTestBuild(2);
  }

  @Test
  public void testMoveToplevelClassToAnotherFile() {
    doTest();
  }

  @Test
  public void testMoveClassToAnotherRoot() {
    doTest();
  }

  @Test
  public void testIntegrateOnNonIncrementalMake() {
    PlatformTestUtil.withSystemProperty(BuildDataManager.PROCESS_CONSTANTS_NON_INCREMENTAL_PROPERTY, String.valueOf(true), () -> doTest());
  }

  @Test
  public void testConflictingClasses() {
    JpsModule module1 = addModule("module1", "module1/src");
    JpsModule module2 = addModule("module2", "module2/src");
    JpsModuleRootModificationUtil.addDependency(module2, module1);
    doTestBuild(1).assertSuccessful();
  }
}
