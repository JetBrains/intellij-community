// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ether;

import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.model.JpsModuleRootModificationUtil;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.module.JpsModule;
import org.junit.jupiter.api.Test;

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

  @Test
  public void testConstantChain() {
    doTest();
  }

  @Test
  public void testConstantChain1() {
    doTest();
  }

  @Test
  public void testConstantChain2() {
    doTest();
  }

  @Test
  public void testConstantChain3() {
    doTest();
  }

  @Test
  public void testConstantChainMultiModule() {
    JpsModule moduleA = addModule("moduleA", "moduleA/src");
    JpsModule moduleB = addModule("moduleB", "moduleB/src");
    JpsModule moduleC = addModule("moduleC", "moduleC/src");
    JpsModuleRootModificationUtil.addDependency(moduleB, moduleA);
    JpsModuleRootModificationUtil.addDependency(moduleC, moduleB);
    doTestBuild(1).assertSuccessful();
  }

  @Test
  public void testConstantRemove() {
    doTest();
  }

  @Test
  public void testConstantRemove1() {
    doTest();
  }

  @Test
  public void testDoubleConstantChange() {
    doTest();
  }

  @Test
  public void testFloatConstantChange() {
    doTest();
  }

  @Test
  public void testInnerConstantChange() {
    doTest();
  }

  @Test
  public void testIntConstantChange() {
    doTest();
  }

  @Test
  public void testIntNonStaticConstantChange() {
    doTest();
  }

  @Test
  public void testLongConstantChange() {
    doTest();
  }

  @Test
  public void testNonCompileTimeConstant() {
    doTest();
  }

  @Test
  public void testStringConstantChange() {
    doTest();
  }

  @Test
  public void testStringConstantChangeWithECJ() {
    setupInitialProject();
    final JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getCompilerConfiguration(myProject);
    config.setJavaCompilerId(JavaCompilers.ECLIPSE_ID);

    doTestBuild(1);
  }

  @Test
  public void testStringConstantLessAccessible() {
    doTest();
  }

  @Test
  public void testTypeChange() {
    doTest();
  }

  @Test
  public void testTypeChange1() {
    doTest();
  }

  @Test
  public void testTypeChange2() {
    doTest();
  }

  @Test
  public void testNonIncremental1() {
    doTest();
  }

  @Test
  public void testNonIncremental2() {
    doTest();
  }

  @Test
  public void testNonIncremental3() {
    doTest();
  }

  @Test
  public void testNonIncremental4() {
    doTest();
  }

  @Test
  public void testMutualConstants() {
    doTest();
  }
}
