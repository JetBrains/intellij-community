package org.jetbrains.ether;

import org.jetbrains.jps.model.JpsModuleRootModificationUtil;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;
import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 04.10.11
 * Time: 14:41
 * To change this template use File | Settings | File Templates.
 */
public class MarkDirtyTest extends IncrementalTestCase {
  public MarkDirtyTest() throws Exception {
    super("markDirty");
  }

  public void testRecompileDependent() throws Exception {
    doTest();
  }

  public void testRecompileDependentTests() throws Exception {
    JpsModule module = addModule();
    module.addSourceRoot(getUrl("testSrc"), JavaSourceRootType.TEST_SOURCE);
    JpsLibrary library = addLibrary();
    JpsModuleRootModificationUtil.addDependency(module, library, JpsJavaDependencyScope.TEST, false);
    doTestBuild().assertSuccessful();
  }

  private JpsLibrary addLibrary() {
    JpsLibrary library = myJpsProject.addLibrary("l", JpsJavaLibraryType.INSTANCE);
    library.addRoot(new File(getAbsolutePath("lib/a.jar")), JpsOrderRootType.COMPILED);
    return library;
  }
}
