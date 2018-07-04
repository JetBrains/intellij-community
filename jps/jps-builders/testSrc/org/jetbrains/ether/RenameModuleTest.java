// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ether;

import org.jetbrains.jps.model.JpsModuleRootModificationUtil;
import org.jetbrains.jps.model.module.JpsModule;

public class RenameModuleTest extends IncrementalTestCase {
  JpsModule myModuleToRename;

  public RenameModuleTest() {
    super("renameModule");
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