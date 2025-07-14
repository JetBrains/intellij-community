// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ether;

import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.model.JpsModuleRootModificationUtil;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.Set;

public class FieldPropertyTest extends IncrementalTestCase {
  private static final Set<String> GRAPH_ONLY_TESTS = Set.of("constantChain3");

  public FieldPropertyTest() {
    super("fieldProperties");
  }

  @Override
  protected boolean shouldRunTest() {
    if (JavaBuilderUtil.isDepGraphEnabled()) {
      return super.shouldRunTest();
    }
    return !GRAPH_ONLY_TESTS.contains(getTestName(true));
  }

  public void testConstantChain() {
    doTest();
  }

  public void testConstantChain1() {
    doTest();
  }

  public void testConstantChain2() {
    doTest();
  }

  public void testConstantChain3() {
    doTest();
  }

  public void testConstantChainMultiModule() {
    JpsModule moduleA = addModule("moduleA", "moduleA/src");
    JpsModule moduleB = addModule("moduleB", "moduleB/src");
    JpsModule moduleC = addModule("moduleC", "moduleC/src");
    JpsModuleRootModificationUtil.addDependency(moduleB, moduleA);
    JpsModuleRootModificationUtil.addDependency(moduleC, moduleB);
    doTestBuild(1).assertSuccessful();
  }

  public void testConstantRemove() {
    doTest();
  }

  public void testConstantRemove1() {
    doTest();
  }

  public void testDoubleConstantChange() {
    doTest();
  }

  public void testFloatConstantChange() {
    doTest();
  }

  public void testInnerConstantChange() {
    doTest();
  }

  public void testIntConstantChange() {
    doTest();
  }

  public void testIntNonStaticConstantChange() {
    doTest();
  }

  public void testLongConstantChange() {
    doTest();
  }

  public void testNonCompileTimeConstant() {
    doTest();
  }

  public void testStringConstantChange() {
    doTest();
  }

  public void testStringConstantChangeWithECJ() {
    setupInitialProject();
    final JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getCompilerConfiguration(myProject);
    config.setJavaCompilerId(JavaCompilers.ECLIPSE_ID);

    doTestBuild(1);
  }

  public void testStringConstantLessAccessible() {
    doTest();
  }

  public void testTypeChange() {
    doTest();
  }

  public void testTypeChange1() {
    doTest();
  }

  public void testTypeChange2() {
    doTest();
  }

  public void testNonIncremental1() {
    doTest();
  }

  public void testNonIncremental2() {
    doTest();
  }

  public void testNonIncremental3() {
    doTest();
  }

  public void testNonIncremental4() {
    doTest();
  }

  public void testMutualConstants() {
    doTest();
  }
}
