package org.jetbrains.ether;

import org.jetbrains.jps.model.JpsModuleRootModificationUtil;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author: db
 * Date: 04.10.11
 */
public class MarkDirtyTest extends IncrementalTestCase {
  public MarkDirtyTest() throws Exception {
    super("markDirty");
  }

  public void testRecompileDependent() {
    doTest();
  }

  public void testRecompileDependentTests() {
    JpsModule module = addModule();
    addTestRoot(module, "testSrc");
    JpsLibrary library = addLibrary("lib/a.jar");
    JpsModuleRootModificationUtil.addDependency(module, library, JpsJavaDependencyScope.TEST, false);
    doTestBuild(1).assertSuccessful();
  }

  public void testTransitiveRecompile() {
    JpsModule module = addModule();
    addTestRoot(module, "testSrc");
    JpsModule util = addModule("util", "util/src");
    addTestRoot(util, "util/testSrc");
    JpsModuleRootModificationUtil.addDependency(module, util);
    JpsModule lib = addModule("lib", "lib/src");
    addTestRoot(lib, "lib/testSrc");
    JpsModuleRootModificationUtil.addDependency(util, lib);
    doTestBuild(1).assertSuccessful();
  }
}
