/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.module.impl.ModuleEx;
import com.intellij.openapi.module.impl.scopes.LibraryScope;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.ModuleTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.util.PathsList;

import java.io.IOException;
import java.util.Collections;

/**
 * @author yole
 */
public class ModuleScopesTest extends ModuleTestCase {
  private LightTempDirTestFixtureImpl myFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture = new LightTempDirTestFixtureImpl();
    myFixture.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
    super.tearDown();
  }

  public void testBasics() throws Exception {
    Module moduleA = createModule("a.iml", StdModuleTypes.JAVA);
    addDependentModule(moduleA, DependencyScope.COMPILE);
    addLibrary(moduleA, DependencyScope.COMPILE);

    VirtualFile classB = myFixture.createFile("b/Test.java", "public class Test { }");
    VirtualFile libraryClass = myFixture.createFile("lib/Test.class");

    assertFalse(moduleA.getModuleScope().contains(classB));
    assertFalse(moduleA.getModuleScope().contains(libraryClass));

    assertFalse(moduleA.getModuleWithLibrariesScope().contains(classB));
    assertTrue(moduleA.getModuleWithLibrariesScope().contains(libraryClass));

    assertTrue(moduleA.getModuleWithDependenciesScope().contains(classB));
    assertFalse(moduleA.getModuleWithDependenciesScope().contains(libraryClass));

    assertTrue(moduleA.getModuleWithDependenciesAndLibrariesScope(true).contains(classB));
    assertTrue(moduleA.getModuleWithDependenciesAndLibrariesScope(true).contains(libraryClass));

    assertTrue(moduleA.getModuleRuntimeScope(true).contains(classB));
    assertTrue(moduleA.getModuleRuntimeScope(true).contains(libraryClass));
  }

  public void testLibraryScope() throws IOException {
    VirtualFile libraryClass = myFixture.createFile("lib/classes/Test.class");
    VirtualFile librarySrc = myFixture.createFile("lib/src/Test.java", "public class Test { }");
    Library library = PsiTestUtil.addProjectLibrary(myModule, "my-lib", Collections.singletonList(libraryClass.getParent()),
                                                    Collections.singletonList(librarySrc.getParent()));
    LibraryScope scope = new LibraryScope(myProject, library);
    assertTrue(scope.contains(libraryClass));
    assertTrue(scope.contains(librarySrc));
  }

  public void testTestOnlyModuleDependency() throws Exception {
    Module moduleA = createModule("a.iml", StdModuleTypes.JAVA);
    Module moduleB = addDependentModule(moduleA, DependencyScope.TEST);

    VirtualFile classB = myFixture.createFile("b/Test.java", "public class Test { }");
    assertTrue(moduleA.getModuleWithDependenciesAndLibrariesScope(true).contains(classB));
    assertFalse(moduleA.getModuleWithDependenciesAndLibrariesScope(false).contains(classB));
    assertFalse(moduleA.getModuleWithDependenciesAndLibrariesScope(false).isSearchInModuleContent(moduleB));

    final VirtualFile[] compilationClasspath = getCompilationClasspath(moduleA);
    assertEquals(1, compilationClasspath.length);
    final VirtualFile[] productionCompilationClasspath = getProductionCompileClasspath(moduleA);
    assertEmpty(productionCompilationClasspath);

    final PathsList pathsList = OrderEnumerator.orderEntries(moduleA).recursively().getPathsList();
    assertEquals(1, pathsList.getPathList().size());
    final PathsList pathsListWithoutTests = OrderEnumerator.orderEntries(moduleA).productionOnly().recursively().getPathsList();
    assertEquals(0, pathsListWithoutTests.getPathList().size());
  }

  private Module addDependentModule(final Module moduleA, final DependencyScope scope) {
    return addDependentModule("b", moduleA, scope, false);
  }

  private Module addDependentModule(final String name, final Module moduleA,
                                    final DependencyScope scope,
                                    final boolean exported) {
    final Module moduleB = createModule(name + ".iml", StdModuleTypes.JAVA);

    ApplicationManager.getApplication().runWriteAction(() -> {
      VirtualFile rootB = myFixture.findOrCreateDir(name);
      VirtualFile outB = myFixture.findOrCreateDir("out");

      ModuleRootModificationUtil.addDependency(moduleA, moduleB, scope, exported);

      PsiTestUtil.addSourceRoot(moduleB, rootB);
      PsiTestUtil.setCompilerOutputPath(moduleB, outB.getUrl(), false);
    });

    return moduleB;
  }

  public void testModuleTwiceInDependents() {
    Module m = createModule("m.iml", StdModuleTypes.JAVA);
    Module a = createModule("a.iml", StdModuleTypes.JAVA);
    Module b = createModule("b.iml", StdModuleTypes.JAVA);
    Module c = createModule("c.iml", StdModuleTypes.JAVA);

    ModuleRootModificationUtil.addDependency(a, m, DependencyScope.COMPILE, false);
    ModuleRootModificationUtil.addDependency(b, m, DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(a, b, DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(c, a, DependencyScope.COMPILE, true);
    
    VirtualFile root = myFixture.findOrCreateDir("c");
    PsiTestUtil.addSourceContentToRoots(c, root);
    VirtualFile file = createChildData(root, "x.txt");

    GlobalSearchScope deps = m.getModuleWithDependentsScope();
    assertTrue(deps.contains(file));
  }

  public void testModuleContentWithDependenciesScopeRootOrdering() {
    Module m = createModule("m.iml", StdModuleTypes.JAVA);
    Module a = createModule("a.iml", StdModuleTypes.JAVA);
    Module b = createModule("b.iml", StdModuleTypes.JAVA);
    Module c = createModule("c.iml", StdModuleTypes.JAVA);

    ModuleRootModificationUtil.addDependency(b, m, DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(a, b, DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(a, m, DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(c, a, DependencyScope.COMPILE, true);

    VirtualFile mRoot = myFixture.findOrCreateDir("m");
    PsiTestUtil.addSourceContentToRoots(m, mRoot);
    VirtualFile aRoot = myFixture.findOrCreateDir("a");
    PsiTestUtil.addSourceContentToRoots(a, aRoot);
    VirtualFile bRoot = myFixture.findOrCreateDir("b");
    PsiTestUtil.addSourceContentToRoots(b, bRoot);
    VirtualFile cRoot = myFixture.findOrCreateDir("c");
    PsiTestUtil.addSourceContentToRoots(c, cRoot);
    VirtualFile file = createChildData(cRoot, "x.txt");

    GlobalSearchScope deps = c.getModuleContentWithDependenciesScope();
    assertTrue(deps.contains(file));

    assertTrue(deps.compare(mRoot, aRoot) < 0);
    assertTrue(deps.compare(mRoot, bRoot) < 0);
    assertTrue(deps.compare(mRoot, cRoot) < 0);
    assertTrue(deps.compare(bRoot, aRoot) < 0);
    assertTrue(deps.compare(bRoot, cRoot) < 0);
    assertTrue(deps.compare(aRoot, cRoot) < 0);
    assertTrue(deps.compare(cRoot, mRoot) > 0);
    assertTrue(deps.compare(cRoot, aRoot) > 0);
    assertTrue(deps.compare(cRoot, bRoot) > 0);
    assertEquals(0, deps.compare(cRoot, cRoot));
  }

  public void testTestOnlyLibraryDependency() {
    Module m = createModule("a.iml", StdModuleTypes.JAVA);
    addLibrary(m, DependencyScope.TEST);
    VirtualFile libraryClass = myFixture.createFile("lib/Test.class");

    assertTrue(m.getModuleWithDependenciesAndLibrariesScope(true).contains(libraryClass));
    assertFalse(m.getModuleWithDependenciesAndLibrariesScope(false).contains(libraryClass));

    final VirtualFile[] compilationClasspath = getCompilationClasspath(m);
    assertEquals(1, compilationClasspath.length);
    final VirtualFile[] productionCompilationClasspath = getProductionCompileClasspath(m);
    assertEmpty(productionCompilationClasspath);
  }

  public void testRuntimeModuleDependency() {
    Module moduleA = createModule("a.iml", StdModuleTypes.JAVA);
    addDependentModule(moduleA, DependencyScope.RUNTIME);
    final VirtualFile[] runtimeClasspath = getRuntimeClasspath(moduleA);
    assertEquals(1, runtimeClasspath.length);
    final VirtualFile[] compilationClasspath = getCompilationClasspath(moduleA);
    assertEquals(1, compilationClasspath.length);
    VirtualFile[] production = getProductionCompileClasspath(moduleA);
    assertEmpty(production);
  }

  public void testRuntimeLibraryDependency() {
    Module m = createModule("a.iml", StdModuleTypes.JAVA);
    VirtualFile libraryRoot = addLibrary(m, DependencyScope.RUNTIME);

    final VirtualFile[] runtimeClasspath = getRuntimeClasspath(m);
    assertOrderedEquals(runtimeClasspath, libraryRoot);

    final VirtualFile[] compilationClasspath = getCompilationClasspath(m);
    assertEquals(1, compilationClasspath.length);
    VirtualFile[] production = getProductionCompileClasspath(m);
    assertEmpty(production);

    VirtualFile libraryClass = myFixture.createFile("lib/Test.class");
    assertFalse(m.getModuleWithDependenciesAndLibrariesScope(true).contains(libraryClass));
    assertFalse(m.getModuleWithDependenciesAndLibrariesScope(false).contains(libraryClass));

    assertTrue(m.getModuleRuntimeScope(true).contains(libraryClass));
    assertTrue(m.getModuleRuntimeScope(false).contains(libraryClass));
  }

  public void testProvidedModuleDependency() {
    Module moduleA = createModule("a.iml", StdModuleTypes.JAVA);
    addDependentModule(moduleA, DependencyScope.PROVIDED);
    VirtualFile[] runtimeClasspath = getRuntimeClasspath(moduleA);
    assertEmpty(runtimeClasspath);
    final VirtualFile[] compilationClasspath = getCompilationClasspath(moduleA);
    assertEquals(1, compilationClasspath.length);
  }

  public void testProvidedLibraryDependency() {
    Module m = createModule("a.iml", StdModuleTypes.JAVA);
    VirtualFile libraryRoot = addLibrary(m, DependencyScope.PROVIDED);

    final VirtualFile[] runtimeClasspath = getRuntimeClasspath(m);
    assertEmpty(runtimeClasspath);

    final VirtualFile[] compilationClasspath = getCompilationClasspath(m);
    assertOrderedEquals(compilationClasspath, libraryRoot);

    VirtualFile libraryClass = myFixture.createFile("lib/Test.class");
    assertTrue(m.getModuleWithDependenciesAndLibrariesScope(true).contains(libraryClass));
    assertTrue(m.getModuleWithDependenciesAndLibrariesScope(false).contains(libraryClass));

    assertTrue(m.getModuleRuntimeScope(true).contains(libraryClass));
    assertTrue(m.getModuleRuntimeScope(false).contains(libraryClass));
  }

  private static VirtualFile[] getRuntimeClasspath(Module m) {
    return ModuleRootManager.getInstance(m).orderEntries().productionOnly().runtimeOnly().recursively().getClassesRoots();
  }

  private static VirtualFile[] getProductionCompileClasspath(Module moduleA) {
    return ModuleRootManager.getInstance(moduleA).orderEntries().productionOnly().compileOnly().recursively().exportedOnly()
      .getClassesRoots();
  }

  private static VirtualFile[] getCompilationClasspath(Module m) {
    return ModuleRootManager.getInstance(m).orderEntries().recursively().exportedOnly().getClassesRoots();
  }

  private VirtualFile addLibrary(final Module m, final DependencyScope scope) {
    final VirtualFile libraryRoot = myFixture.findOrCreateDir("lib");

    ModuleRootModificationUtil.addModuleLibrary(m, "l", Collections.singletonList(libraryRoot.getUrl()),
                                                Collections.emptyList(), scope);
    return libraryRoot;
  }

  public void testLibUnderModuleContent() {
    VirtualFile lib = myFixture.findOrCreateDir("lib");
    PsiTestUtil.addContentRoot(myModule, lib);

    VirtualFile file = createChildData(lib, "a.txt");
    addLibrary(myModule, DependencyScope.COMPILE);
    assertTrue(myModule.getModuleWithDependenciesAndLibrariesScope(false).contains(file));
  }

  public void testScopeEquality() {
    Module module = createModule("a.iml", StdModuleTypes.JAVA);
    addDependentModule(module, DependencyScope.COMPILE);
    addLibrary(module, DependencyScope.COMPILE);

    GlobalSearchScope deps = module.getModuleWithDependentsScope();
    GlobalSearchScope depsTests = module.getModuleTestsWithDependentsScope();

    assertFalse(deps.equals(depsTests));
    assertFalse(depsTests.equals(deps));

    ((ModuleEx)module).clearScopesCache();

    GlobalSearchScope deps2 = module.getModuleWithDependentsScope();
    GlobalSearchScope depsTests2 = module.getModuleTestsWithDependentsScope();

    assertFalse(deps2.equals(depsTests2));
    assertFalse(depsTests2.equals(deps2));
    assertNotSame(deps, deps2);
    assertNotSame(depsTests, depsTests2);
    assertEquals(deps, deps2);
    assertEquals(depsTests, depsTests2);
  }

  public void testHonorExportsWhenCalculatingLibraryScope() throws IOException {
    Module a = createModule("a.iml", StdModuleTypes.JAVA);
    Module b = createModule("b.iml", StdModuleTypes.JAVA);
    Module c = createModule("c.iml", StdModuleTypes.JAVA);
    ModuleRootModificationUtil.addDependency(a, b, DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(b, c, DependencyScope.COMPILE, true);

    final VirtualFile libFile1 = myFixture.createFile("lib1/a.txt", "");
    final VirtualFile libFile2 = myFixture.createFile("lib2/a.txt", "");

    ModuleRootModificationUtil.addModuleLibrary(a, "l", Collections.singletonList(libFile1.getParent().getUrl()),
                                                Collections.emptyList(), Collections.emptyList(), DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addModuleLibrary(c, "l", Collections.singletonList(libFile2.getParent().getUrl()),
                                                Collections.emptyList(), Collections.emptyList(), DependencyScope.COMPILE, true);

    assertTrue(ResolveScopeManager.getElementResolveScope(getPsiManager().findFile(libFile1)).contains(libFile2));
    assertTrue(ResolveScopeManager.getElementResolveScope(getPsiManager().findFile(libFile2)).contains(libFile1));
  }
}
