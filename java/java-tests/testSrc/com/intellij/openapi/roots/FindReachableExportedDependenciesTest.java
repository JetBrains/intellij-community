// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.testFramework.ModuleTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.util.*;

public class FindReachableExportedDependenciesTest extends ModuleTestCase {
  public void testModuleDependency() {
    Module a = createModule("a");
    Module b = createModule("b");
    Module c = createModule("c");
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(b, c, DependencyScope.COMPILE, true);
    OrderEntry dependency = assertOneElement(findReachableViaThisDependencyOnly(a, b)).getKey();
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
    OrderEntry dependency = assertOneElement(findReachableViaThisDependencyOnly(a, b)).getKey();
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

  public void testExportedOfExportedDependency() {
    Module a = createModule("a");
    Module b = createModule("b");
    Module c = createModule("c");
    Module d = createModule("d");
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(b, c, DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(c, d, DependencyScope.COMPILE, true);
    List<Map.Entry<OrderEntry, OrderEntry>> result = new ArrayList<>(findReachableViaThisDependencyOnly(a, b));
    assertEquals(2, result.size());
    assertSame(c, ((ModuleOrderEntry)result.get(0).getKey()).getModule());
    assertSame(c, ((ModuleOrderEntry)result.get(0).getValue()).getModule());
    assertSame(d, result.get(1).getKey().getOwnerModule());
    assertSame(c, ((ModuleOrderEntry)result.get(1).getValue()).getModule());
  }

  public void testIgnoreDirectRuntimeDependency() {
    Module a = createModule("a");
    Module b = createModule("b");
    Module c = createModule("c");
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(a, c, DependencyScope.RUNTIME, false);
    ModuleRootModificationUtil.addDependency(b, c, DependencyScope.COMPILE, true);
    OrderEntry dependency = assertOneElement(findReachableViaThisDependencyOnly(a, b)).getKey();
    assertSame(c, ((ModuleOrderEntry)dependency).getModule());
  }

  public void testIgnoreDirectTestDependency() {
    Module a = createModule("a");
    Module b = createModule("b");
    Module c = createModule("c");
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(a, c, DependencyScope.TEST, false);
    ModuleRootModificationUtil.addDependency(b, c, DependencyScope.COMPILE, true);
    OrderEntry dependency = assertOneElement(findReachableViaThisDependencyOnly(a, b)).getKey();
    assertSame(c, ((ModuleOrderEntry)dependency).getModule());
  }

  public void testHonorDirectTestDependencyWhenAnalyzingTestDependency() {
    Module a = createModule("a");
    Module b = createModule("b");
    Module c = createModule("c");
    ModuleRootModificationUtil.addDependency(a, b, DependencyScope.TEST, false);
    ModuleRootModificationUtil.addDependency(a, c, DependencyScope.TEST, false);
    ModuleRootModificationUtil.addDependency(b, c, DependencyScope.COMPILE, true);
    assertEmpty(findReachableViaThisDependencyOnly(a, b));
  }

  private Set<Map.Entry<OrderEntry, OrderEntry>> findReachableViaThisDependencyOnly(Module a, Module b) {
    ModulesProvider rootModelProvider = DefaultModulesProvider.createForProject(myProject);
    return JavaProjectRootsUtil.findExportedDependenciesReachableViaThisDependencyOnly(a, b, rootModelProvider).entrySet();
  }
}
