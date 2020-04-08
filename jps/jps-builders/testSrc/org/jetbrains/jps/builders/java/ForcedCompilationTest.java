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
package org.jetbrains.jps.builders.java;

import com.intellij.util.PathUtil;
import org.jetbrains.jps.builders.CompileScopeTestBuilder;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaModuleExtension;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;

public class ForcedCompilationTest extends JpsBuildTestCase {
  public void testRecompileDependentAfterForcedCompilation() {
    String srcRoot = PathUtil.getParentPath(createFile("src/A.java", "class A{ { new B(); } }"));
    String b = createFile("depSrc/B.java", "public class B{}");
    JpsModule main = addModule("main", srcRoot);
    JpsModule dep = addModule("dep", PathUtil.getParentPath(b));
    main.getDependenciesList().addModuleDependency(dep);
    rebuildAllModules();

    change(b, "public class B{ public B(int i){} }");
    doBuild(CompileScopeTestBuilder.recompile().module(dep)).assertSuccessful();
    doBuild(CompileScopeTestBuilder.make().module(main)).assertFailed();
  }

  public void testClearModuleOutputOnForcedCompilation() {
    String srcRoot = PathUtil.getParentPath(createFile("src/A.java", "class A{ }"));
    JpsModule main = addModule("main", srcRoot);
    rebuildAllModules();

    File b = new File(createFile(getModuleOutputRelativePath(main) + "/a.txt", "qwerty"));
    buildAllModules();
    assertExists(b);

    doBuild(CompileScopeTestBuilder.recompile().module(main)).assertSuccessful();
    assertDoesntExist(b);
  }

  public void testDontClearModuleOutputOnForcedCompilation() {
    JpsModule m1 = addModule("m1", PathUtil.getParentPath(createFile("src/A.java", "class A{ }")));
    JpsModule m2 = addModule("m2", PathUtil.getParentPath(createFile("src/B.java", "class B{ }")));
    JpsJavaModuleExtension m1Ext = JpsJavaExtensionService.getInstance().getOrCreateModuleExtension(m1);
    JpsJavaModuleExtension m2Ext = JpsJavaExtensionService.getInstance().getOrCreateModuleExtension(m2);
    m2Ext.setOutputUrl(m1Ext.getOutputUrl());
    m2Ext.setTestOutputUrl(m1Ext.getOutputUrl());
    rebuildAllModules();

    File b = new File(createFile(getModuleOutputRelativePath(m1) + "/a.txt", "qwerty"));
    buildAllModules();
    assertExists(b);
    assertExists(new File(getOrCreateProjectDir(), getModuleOutputRelativePath(m1) + "/A.class"));
    assertExists(new File(getOrCreateProjectDir(), getModuleOutputRelativePath(m1) + "/B.class"));

    doBuild(CompileScopeTestBuilder.recompile().module(m1)).assertSuccessful();
    assertExists(b);
    assertExists(new File(getOrCreateProjectDir(), getModuleOutputRelativePath(m1) + "/A.class"));
    assertExists(new File(getOrCreateProjectDir(), getModuleOutputRelativePath(m1) + "/B.class"));
  }
}
