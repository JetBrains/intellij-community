// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ether;

import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.model.JpsModuleRootModificationUtil;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.Set;

public class RenameModuleTest extends IncrementalTestCase {
  private static final Set<String> GRAPH_ONLY_TESTS = Set.of("deleteClassSameModule");

  JpsModule myModuleToRename;

  public RenameModuleTest() {
    super("renameModule");
  }

  @Override
  protected boolean shouldRunTest() {
    if (JavaBuilderUtil.isDepGraphEnabled()) {
      return super.shouldRunTest();
    }
    return !GRAPH_ONLY_TESTS.contains(getTestName(true));
  }

  @Override
  protected void tearDown() throws Exception {
    myModuleToRename = null;
    super.tearDown();
  }

  @Override
  protected void modify(int stage) {
    if (stage == 0) {
      final JpsModule toRename = myModuleToRename;
      if (toRename != null) {
        myModuleToRename = null;
        final String name = toRename.getName();
        toRename.setName(name + "_renamed");
      }
    }
    super.modify(stage);
  }

  @Override
  protected boolean useCachedProjectDescriptorOnEachMake() {
    return false;
  }

  public void testDeleteClassSameModule() {
    myModuleToRename = addModule("moduleA", "moduleA/src");
    doTestBuild(1).assertSuccessful();
  }

  public void testDeleteClassDependentModule() {
    JpsModule moduleA = addModule("moduleA", "moduleA/src");
    JpsModule moduleB = addModule("moduleB", "moduleB/src");
    JpsModuleRootModificationUtil.addDependency(moduleB, moduleA);
    myModuleToRename = moduleB;
    doTestBuild(2).assertSuccessful();
  }
}