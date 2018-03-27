// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ether;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.jps.builders.BuildResult;
import org.jetbrains.jps.builders.CompileScopeTestBuilder;
import org.jetbrains.jps.model.JpsModuleRootModificationUtil;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class Java9Test extends IncrementalTestCase {

  private static final String MODULE_DIR_PREFIX = "module";
  private static boolean IS_AT_LEAST_JAVA9 = SystemInfo.isJavaVersionAtLeast("9");
  public Java9Test() {
    super("java9-features");
  }

  protected boolean shouldRunTest() {
    if (!IS_AT_LEAST_JAVA9) {
      System.out.println("Test '" + getTestName(false) + "' skipped because it requires at least java 9 runtime");
      return false;
    }
    return super.shouldRunTest();
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
  
  protected BuildResult doTestBuild(int makesCount) {
    setupModules();
    return super.doTestBuild(makesCount);
  }

  private void setupModules() {
    final File projectDir = getOrCreateProjectDir();
    final File[] moduleDirs = projectDir.listFiles((dir, name) -> name.startsWith(MODULE_DIR_PREFIX));

    if (moduleDirs != null && moduleDirs.length > 0) {
      final Map<String, JpsModule> modules = new HashMap<>();
      final List<String> moduleNames = new ArrayList<>();
      for (File moduleDir : moduleDirs) {
        final String name = moduleDir.getName().substring(MODULE_DIR_PREFIX.length());
        final JpsModule m = addModule(name, moduleDir.getName() + "/src");
        modules.put(name, m);
        moduleNames.add(name);
      }
      Collections.sort(moduleNames, Collections.reverseOrder());
      // set dependencies in alphabet reverse order
      JpsModule from = null;
      for (String name : moduleNames) {
        final JpsModule mod = modules.get(name);
        if (from != null) {
          JpsModuleRootModificationUtil.addDependency(from, mod);
        }
        from = mod;
      }
    }
  }
}
