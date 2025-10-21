// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ether;

import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.model.JpsModuleRootModificationUtil;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.Set;
import java.util.function.Function;

/**
 * The test checks incremental compilation after module rename.
 * Since renaming isn't supported in JpsModel, it's emulated by removing and adding the module.
 */
public class RenameModuleTest extends IncrementalTestCase {
  private static final Set<String> GRAPH_ONLY_TESTS = Set.of("deleteClassSameModule");

  JpsModule myModuleToRemove;
  Function<String, JpsModule> myAddModuleAction;

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
    myModuleToRemove = null;
    super.tearDown();
  }

  @Override
  protected void modify(int stage) {
    if (stage == 0) {
      final JpsModule toRemove = myModuleToRemove;
      if (toRemove != null) {
        myModuleToRemove = null;
        myProject.removeModule(toRemove);
        String newName = toRemove.getName() + "_renamed";
        myAddModuleAction.apply(newName);
      }
    }
    super.modify(stage);
  }

  @Override
  protected boolean useCachedProjectDescriptorOnEachMake() {
    return false;
  }

  public void testDeleteClassSameModule() {
    myAddModuleAction = name -> addModule(name, "moduleA/src"); 
    myModuleToRemove = myAddModuleAction.apply("moduleA");
    doTestBuild(1).assertSuccessful();
  }

  public void testDeleteClassDependentModule() {
    JpsModule moduleA = addModule("moduleA", "moduleA/src");
    myAddModuleAction = name -> {
      JpsModule moduleB = addModule(name, "moduleB/src");
      JpsModuleRootModificationUtil.addDependency(moduleB, moduleA);
      return moduleB;
    };
    myModuleToRemove = myAddModuleAction.apply("moduleB");
    doTestBuild(2).assertSuccessful();
  }
}