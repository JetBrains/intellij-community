// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ether;

import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.model.JpsModuleRootModificationUtil;
import org.jetbrains.jps.model.module.JpsModule;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class ClassPropertyTest extends IncrementalTestCase {
  private static final Set<String> GRAPH_ONLY_TESTS = Set.of("addImplementsPatternMatching");

  public ClassPropertyTest() {
    super("classProperties");
  }

  @Override
  protected boolean shouldRunTest() {
    if (JavaBuilderUtil.isDepGraphEnabled()) {
      return super.shouldRunTest();
    }
    return !GRAPH_ONLY_TESTS.contains(getTestName(true));
  }

  @Test
  public void testAddExtends() {
    doTest();
  }

  @Test
  public void testAddImplements() {
    doTest();
  }

  @Test
  public void testAddImplementsPatternMatching() {
    doTest();
  }

  @Test
  public void testChangeExtends() {
    doTest();
  }

  @Test
  public void testRemoveExtends() {
    doTest();
  }

  @Test
  public void testRemoveExtendsAffectsFieldAccess() {
    doTest();
  }

  @Test
  public void testRemoveExtendsAffectsMethodAccess() {
    doTest();
  }

  @Test
  public void testRemoveImplements() {
    doTest();
  }

  @Test
  public void testRemoveImplements2() {
    doTest();
  }

  @Test
  public void testRemoveImplements3() {
    doTest();
  }

  @Test
  public void testChangeExtends2() {
      doTest();
  }

  @Test
  public void testConvertToCheckedException() {
      doTest();
  }
  
  @Test
  public void testConvertToCheckedExceptionMultiModule() {
    JpsModule module1 = addModule("module1", "module1/src");
    JpsModule module2 = addModule("module2", "module2/src");
    JpsModuleRootModificationUtil.addDependency(module2, module1);
    doTestBuild(1).assertSuccessful();
  }
}
