// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.testFramework.ModuleTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FindReachableExportedDependenciesTest extends ModuleTestCase {
  public void testModuleDependency() {
    Module a = createModule("a");
    Module b = createModule("b");
    Module c = createModule("c");
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(b, c, DependencyScope.COMPILE, true);
    OrderEntry dependency = assertOneElement(findReachableViaThisDependencyOnly(a, b));
    assertSame(c, ((ModuleOrderEntry)dependency).getModule());
    ModuleRootModificationUtil.addDependency(a, c);
    assertEmpty(findReachableViaThisDependencyOnly(a, b));
  }

  public void testModuleDependencyNotExported() {
    Module a = createModule("a");
    Module b = createModule("b");
    Module c = createModule("c");
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(b, c);
    assertEmpty(findReachableViaThisDependencyOnly(a, b));
  }

  public void testLibraryDependency() {
    Module a = createModule("a");
    Module b = createModule("b");
    Library lib = PsiTestUtil.addProjectLibrary(myModule, "lib", Arrays.asList(), Collections.emptyList());
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(b, lib, DependencyScope.COMPILE, true);
    OrderEntry dependency = assertOneElement(findReachableViaThisDependencyOnly(a, b));
    assertSame(lib, ((LibraryOrderEntry)dependency).getLibrary());
    ModuleRootModificationUtil.addDependency(a, lib);
    assertEmpty(findReachableViaThisDependencyOnly(a, b));
  }

  public void testLibraryDependencyNotExported() {
    Module a = createModule("a");
    Module b = createModule("b");
    Library lib = PsiTestUtil.addProjectLibrary(myModule, "lib", Arrays.asList(), Collections.emptyList());
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(b, lib);
    assertEmpty(findReachableViaThisDependencyOnly(a, b));
  }

  public void testTwoPaths() {
    Module a = createModule("a");
    Module b1 = createModule("b1");
    Module b2 = createModule("b2");
    Module c = createModule("c");
    ModuleRootModificationUtil.addDependency(a, b1);
    ModuleRootModificationUtil.addDependency(a, b2);
    ModuleRootModificationUtil.addDependency(b1, c, DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(b2, c, DependencyScope.COMPILE, true);
    assertEmpty(findReachableViaThisDependencyOnly(a, b1));
  }


  private List<OrderEntry> findReachableViaThisDependencyOnly(Module a, Module b) {
    ModulesProvider rootModelProvider = DefaultModulesProvider.createForProject(myProject);
    return JavaProjectRootsUtil.findExportedDependenciesReachableViaThisDependencyOnly(a, b, rootModelProvider);
  }
}
