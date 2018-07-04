// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ether;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.jps.builders.BuildResult;
import org.jetbrains.jps.builders.CompileScopeTestBuilder;

/**
 * @author Eugene Zhuravlev
 */
public class Java9Test extends IncrementalTestCase {

  public Java9Test() {
    super("java9-features");
  }

  protected boolean shouldRunTest() {
    if (!SystemInfo.IS_AT_LEAST_JAVA9) {
      System.out.println("Test '" + getTestName(false) + "' skipped because it requires at least java 9 runtime");
      return false;
    }
    return super.shouldRunTest();
  }

  protected BuildResult doTestBuild(int makesCount) {
    setupModules();
    return super.doTestBuild(makesCount);
  }

  public void testModuleInfoAdded() {
    // expected result: the whole target is recompiled after the module-info.java file was newly added
    // because necessary 'require' directives may be missing from the newly added module-info file
    final BuildResult buildResult = doTest();
    buildResult.assertSuccessful();
  }

  public void testRemoveModuleRequires() {
    final BuildResult buildResult = doTest();
    buildResult.assertFailed();
  }

  public void testRemoveTransitiveModuleRequires() {
    final BuildResult buildResult = doTest();
    buildResult.assertFailed();
  }

  public void testChangeTransitiveModuleRequires() {
    final BuildResult buildResult = doTest();
    buildResult.assertFailed();
  }

  public void testChangeQualifiedTransitiveModuleRequires() {
    final BuildResult buildResult = doTest();
    buildResult.assertSuccessful();
  }

  public void testRemoveModuleExports() {
    final BuildResult buildResult = doTest();
    buildResult.assertSuccessful();
  }

  public void testRemoveTransitiveModuleExports() {
    final BuildResult buildResult = doTest();
    buildResult.assertFailed();
  }

  public void testRemoveQualifiedModuleExports() {
    final BuildResult buildResult = doTest();
    buildResult.assertSuccessful();
  }

  public void testRemoveQualifiedTransitiveModuleExports() {
    final BuildResult buildResult = doTest();
    buildResult.assertFailed();
  }

  public void testChangeQualifiedTransitiveModuleExportsNoRebuild() {
    final BuildResult buildResult = doTest();
    buildResult.assertSuccessful();
  }

  public void testChangeQualifiedTransitiveModuleExportsRebuildIndirectDeps() {
    final BuildResult buildResult = doTest();
    buildResult.assertFailed();
  }

  public void testChangeQualifiedTransitiveModuleExportsRebuildDirectDeps() {
    final BuildResult buildResult = doTest();
    buildResult.assertSuccessful();
  }

  public void testIntegrateAfterErrors() {
    setupInitialProject();
    setupModules();

    doBuild(CompileScopeTestBuilder.rebuild().allModules()).assertFailed();
    modify(0);
    doBuild(CompileScopeTestBuilder.make().allModules()).assertSuccessful();
    modify(1);
    doBuild(CompileScopeTestBuilder.make().allModules()).assertFailed();
  }
}
