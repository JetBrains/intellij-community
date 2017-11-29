/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  public MarkDirtyTest() {
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
  
  public void testRecompileTwinDependencies() {
    doTest().assertSuccessful();
  }

  public void testDoNotMarkDirtyCompiledChunks() {
    //'b.Client' from production sources of 'b' cannot depend on 'a.ShortName' from module 'a' so it shouldn't be marked as dirty.
    //Otherwise we can get 'dirty' sources after full make if production of 'b' was compiled before 'a'
    JpsModule b = addModule("b", "moduleB/src");
    addTestRoot(b, "moduleB/testSrc");
    JpsModule a = addModule("a", "moduleA/src");
    addTestRoot(a, "moduleA/testSrc");
    JpsModuleRootModificationUtil.addDependency(b, a, JpsJavaDependencyScope.TEST, false);
    doTestBuild(2).assertSuccessful();
  }
}
