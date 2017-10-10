// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler;

import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.openapi.compiler.CompilerFilter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.io.TestFileSystemBuilder.fs;

/**
 * @author nik
 */
public class UnloadedModulesCompilationTest extends BaseCompilerTestCase {
  public void testDoNotCompileUnloadedModulesByDefault() {
    VirtualFile a = createFile("unloaded/src/A.java", "class A{ error }");
    Module unloaded = addModule("unloaded", a.getParent());
    List<String> unloadedList = Collections.singletonList(unloaded.getName());
    ModuleManager.getInstance(myProject).setUnloadedModules(unloadedList);
    buildAllModules().assertUpToDate();
  }

  public void testCompileUnloadedModulesIfExplicitlySpecified() {
    VirtualFile a = createFile("unloaded/src/A.java", "class A{}");
    Module unloaded = addModule("unloaded", a.getParent());
    File outputDir = getOutputDir(unloaded, false);

    List<String> unloadedList = Collections.singletonList(unloaded.getName());
    ModuleManager.getInstance(myProject).setUnloadedModules(unloadedList);

    make(new ModuleCompileScope(myProject, Collections.emptyList(), unloadedList, true, false), CompilerFilter.ALL);
    fs().file("A.class").build().assertDirectoryEqual(outputDir);
  }
}
