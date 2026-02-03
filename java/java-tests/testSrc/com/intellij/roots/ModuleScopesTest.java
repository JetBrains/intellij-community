// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.scopes.LibraryScope;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.JavaModuleTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.util.PathsList;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertNotEquals;

public class ModuleScopesTest extends JavaModuleTestCase {
  private LightTempDirTestFixtureImpl myFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture = new LightTempDirTestFixtureImpl();
    myFixture.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myFixture = null;
      super.tearDown();
    }
  }

  public void testBasics() throws Exception {
    Module moduleA = createModule("a.iml", JavaModuleType.getModuleType());
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

  public void testContentFileOutsideSourceRoots() throws IOException {
    Module module = createModule("a");
    VirtualFile file = myFixture.createFile("a/data/A.java", "class A {}");
    PsiTestUtil.addContentRoot(module, file.getParent());
    assertFalse(module.getModuleScope().contains(file));
    assertTrue(module.getModuleContentScope().contains(file));
    assertFalse(module.getModuleWithDependenciesScope().contains(file));
    assertTrue(module.getModuleContentWithDependenciesScope().contains(file));
    assertFalse(module.getModuleWithDependenciesAndLibrariesScope(true).contains(file));
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

  public void testLibraryScopeCompare() {
    VirtualFile root1 = myFixture.findOrCreateDir("root1");
    VirtualFile root2 = myFixture.findOrCreateDir("root2");
    Library lib = PsiTestUtil.addProjectLibrary(getModule(), "lib", List.of(root1, root2), Collections.emptyList());
    LibraryScope libScope = new LibraryScope(getProject(), lib);

    // Compare files within the same library.
    VirtualFile file1 = createChildData(root1, "file1");
    VirtualFile file2 = createChildData(root2, "file2");
    assertTrue(libScope.compare(file1, file2) > 0);
    assertTrue(libScope.compare(file2, file1) < 0);
    assertEquals(0, libScope.compare(file1, file1));

    // Compare against files from another library.
    VirtualFile otherRoot = myFixture.findOrCreateDir("otherRoot");
    PsiTestUtil.addProjectLibrary(getModule(), "otherLib", otherRoot);
    VirtualFile fileInOtherLib = createChildData(otherRoot, "fileInOtherLib");
    assertTrue(libScope.compare(file1, fileInOtherLib) > 0);
    assertTrue(libScope.compare(fileInOtherLib, file1) < 0);
    assertEquals(0, libScope.compare(fileInOtherLib, fileInOtherLib));

    // Compare against files from outside the project.
    VirtualFile fileOutsideProject = myFixture.createFile("outsideProject");
    assertTrue(libScope.compare(file1, fileOutsideProject) > 0);
    assertTrue(libScope.compare(fileOutsideProject, file1) < 0);
    assertEquals(0, libScope.compare(fileOutsideProject, fileOutsideProject));
  }

  public void testTestOnlyModuleDependency() throws Exception {
    Module moduleA = createModule("a.iml", JavaModuleType.getModuleType());
    Module moduleB = addDependentModule(moduleA, DependencyScope.TEST);

    VirtualFile classB = myFixture.createFile("b/Test.java", "public class Test { }");
    assertTrue(moduleA.getModuleWithDependenciesAndLibrariesScope(true).contains(classB));
    assertFalse(moduleA.getModuleWithDependenciesAndLibrariesScope(false).contains(classB));
    assertFalse(moduleA.getModuleContentWithDependenciesScope().contains(classB));
    assertFalse(moduleA.getModuleWithDependenciesAndLibrariesScope(false).isSearchInModuleContent(moduleB));

    VirtualFile[] compilationClasspath = getCompilationClasspath(moduleA);
    assertEquals(1, compilationClasspath.length);
    VirtualFile[] productionCompilationClasspath = getProductionCompileClasspath(moduleA);
    assertEmpty(productionCompilationClasspath);

    PathsList pathsList = OrderEnumerator.orderEntries(moduleA).recursively().getPathsList();
    assertEquals(1, pathsList.getPathList().size());
    PathsList pathsListWithoutTests = OrderEnumerator.orderEntries(moduleA).productionOnly().recursively().getPathsList();
    assertEquals(0, pathsListWithoutTests.getPathList().size());
  }

  private Module addDependentModule(Module moduleA, DependencyScope scope) {
    Module moduleB = createModule("b" + ".iml", JavaModuleType.getModuleType());

    ApplicationManager.getApplication().runWriteAction(() -> {
      VirtualFile rootB = myFixture.findOrCreateDir("b");
      VirtualFile outB = myFixture.findOrCreateDir("out");

      ModuleRootModificationUtil.addDependency(moduleA, moduleB, scope, false);

      PsiTestUtil.addSourceRoot(moduleB, rootB);
      PsiTestUtil.setCompilerOutputPath(moduleB, outB.getUrl(), false);
    });

    return moduleB;
  }

  public void testModuleTwiceInDependents() {
    Module m = createModule("m.iml", JavaModuleType.getModuleType());
    Module a = createModule("a.iml", JavaModuleType.getModuleType());
    Module b = createModule("b.iml", JavaModuleType.getModuleType());
    Module c = createModule("c.iml", JavaModuleType.getModuleType());

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
    Module m = createModule("m.iml", JavaModuleType.getModuleType());
    Module a = createModule("a.iml", JavaModuleType.getModuleType());
    Module b = createModule("b.iml", JavaModuleType.getModuleType());
    Module c = createModule("c.iml", JavaModuleType.getModuleType());

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
    Module m = createModule("a.iml", JavaModuleType.getModuleType());
    addLibrary(m, DependencyScope.TEST);
    VirtualFile libraryClass = myFixture.createFile("lib/Test.class");

    assertTrue(m.getModuleWithDependenciesAndLibrariesScope(true).contains(libraryClass));
    assertFalse(m.getModuleWithDependenciesAndLibrariesScope(false).contains(libraryClass));

    VirtualFile[] compilationClasspath = getCompilationClasspath(m);
    assertEquals(1, compilationClasspath.length);
    VirtualFile[] productionCompilationClasspath = getProductionCompileClasspath(m);
    assertEmpty(productionCompilationClasspath);
  }

  public void testRuntimeModuleDependency() {
    Module moduleA = createModule("a.iml", JavaModuleType.getModuleType());
    addDependentModule(moduleA, DependencyScope.RUNTIME);
    VirtualFile[] runtimeClasspath = getRuntimeClasspath(moduleA);
    assertEquals(1, runtimeClasspath.length);
    VirtualFile[] compilationClasspath = getCompilationClasspath(moduleA);
    assertEquals(1, compilationClasspath.length);
    VirtualFile[] production = getProductionCompileClasspath(moduleA);
    assertEmpty(production);
  }

  public void testRuntimeLibraryDependency() {
    Module m = createModule("a.iml", JavaModuleType.getModuleType());
    VirtualFile libraryRoot = addLibrary(m, DependencyScope.RUNTIME);

    VirtualFile[] runtimeClasspath = getRuntimeClasspath(m);
    assertOrderedEquals(runtimeClasspath, libraryRoot);

    VirtualFile[] compilationClasspath = getCompilationClasspath(m);
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
    Module moduleA = createModule("a.iml", JavaModuleType.getModuleType());
    addDependentModule(moduleA, DependencyScope.PROVIDED);
    VirtualFile[] runtimeClasspath = getRuntimeClasspath(moduleA);
    assertEmpty(runtimeClasspath);
    VirtualFile[] compilationClasspath = getCompilationClasspath(moduleA);
    assertEquals(1, compilationClasspath.length);
  }

  public void testProvidedLibraryDependency() {
    Module m = createModule("a.iml", JavaModuleType.getModuleType());
    VirtualFile libraryRoot = addLibrary(m, DependencyScope.PROVIDED);

    VirtualFile[] runtimeClasspath = getRuntimeClasspath(m);
    assertEmpty(runtimeClasspath);

    VirtualFile[] compilationClasspath = getCompilationClasspath(m);
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

  private VirtualFile addLibrary(Module m, DependencyScope scope) {
    VirtualFile libraryRoot = myFixture.findOrCreateDir("lib");

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
    Module module = createModule("a.iml", JavaModuleType.getModuleType());
    addDependentModule(module, DependencyScope.COMPILE);
    addLibrary(module, DependencyScope.COMPILE);

    GlobalSearchScope deps = module.getModuleWithDependentsScope();
    GlobalSearchScope depsTests = module.getModuleTestsWithDependentsScope();

    assertNotEquals(deps, depsTests);
    assertNotEquals(depsTests, deps);

    module.clearScopesCache();

    GlobalSearchScope deps2 = module.getModuleWithDependentsScope();
    GlobalSearchScope depsTests2 = module.getModuleTestsWithDependentsScope();

    assertNotEquals(deps2, depsTests2);
    assertNotEquals(depsTests2, deps2);
    assertNotSame(deps, deps2);
    assertNotSame(depsTests, depsTests2);
    assertEquals(deps, deps2);
    assertEquals(depsTests, depsTests2);
  }

  public void testHonorExportsWhenCalculatingLibraryScope() throws IOException {
    Module a = createModule("a.iml", JavaModuleType.getModuleType());
    Module b = createModule("b.iml", JavaModuleType.getModuleType());
    Module c = createModule("c.iml", JavaModuleType.getModuleType());
    ModuleRootModificationUtil.addDependency(a, b, DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(b, c, DependencyScope.COMPILE, true);

    VirtualFile libFile1 = myFixture.createFile("lib1/a.txt", "");
    VirtualFile libFile2 = myFixture.createFile("lib2/a.txt", "");

    ModuleRootModificationUtil.addModuleLibrary(a, "l", Collections.singletonList(libFile1.getParent().getUrl()),
                                                Collections.emptyList(), Collections.emptyList(), DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addModuleLibrary(c, "l", Collections.singletonList(libFile2.getParent().getUrl()),
                                                Collections.emptyList(), Collections.emptyList(), DependencyScope.COMPILE, true);

    assertTrue(ResolveScopeManager.getElementResolveScope(getPsiManager().findFile(libFile1)).contains(libFile2));
    assertTrue(ResolveScopeManager.getElementResolveScope(getPsiManager().findFile(libFile2)).contains(libFile1));
  }
}
