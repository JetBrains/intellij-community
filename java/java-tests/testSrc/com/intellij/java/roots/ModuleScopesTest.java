// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.roots;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
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
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.PathsList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.createChildData;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


@TestApplication
public class ModuleScopesTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  private LightTempDirTestFixtureImpl myFixture;

  @BeforeEach
  void setUp() throws Exception {
    myFixture = new LightTempDirTestFixtureImpl();
    myFixture.setUp();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (myFixture != null) {
      EdtTestUtil.runInEdtAndWait(() -> myFixture.tearDown());
    }
  }

  @Test
  public void testBasics() throws Exception {
    Module moduleA = heavyFixture.get().createModule(Path.of("a.iml"));
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

  @Test
  public void testContentFileOutsideSourceRoots() throws IOException {
    Module module = heavyFixture.get().createModule("a");
    VirtualFile file = myFixture.createFile("a/data/A.java", "class A {}");
    PsiTestUtil.addContentRoot(module, file.getParent());
    assertFalse(module.getModuleScope().contains(file));
    assertTrue(module.getModuleContentScope().contains(file));
    assertFalse(module.getModuleWithDependenciesScope().contains(file));
    assertTrue(module.getModuleContentWithDependenciesScope().contains(file));
    assertFalse(module.getModuleWithDependenciesAndLibrariesScope(true).contains(file));
  }

  @Test
  public void testLibraryScope() throws IOException {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    VirtualFile libraryClass = myFixture.createFile("lib/classes/Test.class");
    VirtualFile librarySrc = myFixture.createFile("lib/src/Test.java", "public class Test { }");
    Library library = PsiTestUtil.addProjectLibrary(fixture.getModule(), "my-lib", Collections.singletonList(libraryClass.getParent()),
                                                    Collections.singletonList(librarySrc.getParent()));
    LibraryScope scope = new LibraryScope(fixture.getProject(), library);
    assertTrue(scope.contains(libraryClass));
    assertTrue(scope.contains(librarySrc));
  }

  @Test
  public void testLibraryScopeCompare() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    VirtualFile root1 = myFixture.findOrCreateDir("root1");
    VirtualFile root2 = myFixture.findOrCreateDir("root2");
    Library lib = PsiTestUtil.addProjectLibrary(fixture.getModule(), "lib", List.of(root1, root2), Collections.emptyList());
    LibraryScope libScope = new LibraryScope(fixture.getProject(), lib);

    // Compare files within the same library.
    VirtualFile file1 = createChildData(root1, "file1");
    VirtualFile file2 = createChildData(root2, "file2");
    assertTrue(libScope.compare(file1, file2) > 0);
    assertTrue(libScope.compare(file2, file1) < 0);
    assertEquals(0, libScope.compare(file1, file1));

    // Compare against files from another library.
    VirtualFile otherRoot = myFixture.findOrCreateDir("otherRoot");
    PsiTestUtil.addProjectLibrary(fixture.getModule(), "otherLib", otherRoot);
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

  /**
   * A library reachable both transitively-early and directly-late must be ranked by its EARLIEST occurrence
   * on the runtime classpath:
   * <pre>
   *   Main -> Dep1 -> Lib1 (org/example/A)
   *        -> Dep2 -> Lib2 (org/example/A)
   *        -> Lib1            (the same library again, directly, listed last)
   * </pre>
   * Lib1 is first reachable via Dep1 (before Dep2/Lib2), so its {@code A} must win over Lib2's. This regressed
   * when {@code ModuleWithDependenciesScope.calcRootsMultiverse} assigned priorities last-wins: the direct
   * Lib1 entry (listed last) overwrote Lib1's early index, pushing it behind Lib2.
   */
  @Test
  public void testRuntimeScopeRanksLibraryByFirstReachableEntry() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module main = fixture.getModule();
    Module dep1 = fixture.createModule("dep1");
    Module dep2 = fixture.createModule("dep2");

    VirtualFile lib1Root = myFixture.findOrCreateDir("lib1");
    VirtualFile lib2Root = myFixture.findOrCreateDir("lib2");
    VirtualFile a1 = myFixture.createFile("lib1/org/example/A.class");
    VirtualFile a2 = myFixture.createFile("lib2/org/example/A.class");

    Library lib1 = PsiTestUtil.addProjectLibrary(dep1, "lib1", List.of(lib1Root), Collections.emptyList());
    PsiTestUtil.addProjectLibrary(dep2, "lib2", List.of(lib2Root), Collections.emptyList());

    ModuleRootModificationUtil.addDependency(main, dep1);
    ModuleRootModificationUtil.addDependency(main, dep2);
    ModuleRootModificationUtil.addDependency(main, lib1); // the same Lib1, directly — appended last

    GlobalSearchScope runtimeScope = main.getModuleRuntimeScope(true);
    assertTrue(runtimeScope.compare(a1, a2) > 0, "A must resolve from Lib1 (reachable first via Dep1), not Lib2");
    assertTrue(runtimeScope.compare(a2, a1) < 0);
  }

  @Test
  public void testTestOnlyModuleDependency() throws Exception {
    Module moduleA = heavyFixture.get().createModule(Path.of("a.iml"));
    Module moduleB = addDependentModule(moduleA, DependencyScope.TEST);

    VirtualFile classB = myFixture.createFile("b/Test.java", "public class Test { }");
    assertTrue(moduleA.getModuleWithDependenciesAndLibrariesScope(true).contains(classB));
    assertFalse(moduleA.getModuleWithDependenciesAndLibrariesScope(false).contains(classB));
    assertFalse(moduleA.getModuleContentWithDependenciesScope().contains(classB));
    assertFalse(moduleA.getModuleWithDependenciesAndLibrariesScope(false).isSearchInModuleContent(moduleB));

    VirtualFile[] compilationClasspath = getCompilationClasspath(moduleA);
    assertEquals(1, compilationClasspath.length);
    VirtualFile[] productionCompilationClasspath = getProductionCompileClasspath(moduleA);
    assertThat(productionCompilationClasspath).isEmpty();

    PathsList pathsList = OrderEnumerator.orderEntries(moduleA).recursively().getPathsList();
    assertEquals(1, pathsList.getPathList().size());
    PathsList pathsListWithoutTests = OrderEnumerator.orderEntries(moduleA).productionOnly().recursively().getPathsList();
    assertEquals(0, pathsListWithoutTests.getPathList().size());
  }

  private Module addDependentModule(Module moduleA, DependencyScope scope) {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module moduleB = fixture.createModule(Path.of("b" + ".iml"));

    WriteAction.runAndWait(() -> {
      VirtualFile rootB = myFixture.findOrCreateDir("b");
      VirtualFile outB = myFixture.findOrCreateDir("out");

      ModuleRootModificationUtil.addDependency(moduleA, moduleB, scope, false);

      PsiTestUtil.addSourceRoot(moduleB, rootB);
      PsiTestUtil.setCompilerOutputPath(moduleB, outB.getUrl(), false);
    });

    return moduleB;
  }

  @Test
  public void testModuleTwiceInDependents() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module m = fixture.createModule(Path.of("m.iml"));
    Module a = fixture.createModule(Path.of("a.iml"));
    Module b = fixture.createModule(Path.of("b.iml"));
    Module c = fixture.createModule(Path.of("c.iml"));

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

  @Test
  public void testModuleContentWithDependenciesScopeRootOrdering() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module m = fixture.createModule(Path.of("m.iml"));
    Module a = fixture.createModule(Path.of("a.iml"));
    Module b = fixture.createModule(Path.of("b.iml"));
    Module c = fixture.createModule(Path.of("c.iml"));

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

  @Test
  public void testTestOnlyLibraryDependency() {
    Module m = heavyFixture.get().createModule(Path.of("a.iml"));
    addLibrary(m, DependencyScope.TEST);
    VirtualFile libraryClass = myFixture.createFile("lib/Test.class");

    assertTrue(m.getModuleWithDependenciesAndLibrariesScope(true).contains(libraryClass));
    assertFalse(m.getModuleWithDependenciesAndLibrariesScope(false).contains(libraryClass));

    VirtualFile[] compilationClasspath = getCompilationClasspath(m);
    assertEquals(1, compilationClasspath.length);
    VirtualFile[] productionCompilationClasspath = getProductionCompileClasspath(m);
    assertThat(productionCompilationClasspath).isEmpty();
  }

  @Test
  public void testRuntimeModuleDependency() {
    Module moduleA = heavyFixture.get().createModule(Path.of("a.iml"));
    addDependentModule(moduleA, DependencyScope.RUNTIME);
    VirtualFile[] runtimeClasspath = getRuntimeClasspath(moduleA);
    assertEquals(1, runtimeClasspath.length);
    VirtualFile[] compilationClasspath = getCompilationClasspath(moduleA);
    assertEquals(1, compilationClasspath.length);
    VirtualFile[] production = getProductionCompileClasspath(moduleA);
    assertThat(production).isEmpty();
  }

  @Test
  public void testRuntimeLibraryDependency() {
    Module m = heavyFixture.get().createModule(Path.of("a.iml"));
    VirtualFile libraryRoot = addLibrary(m, DependencyScope.RUNTIME);

    VirtualFile[] runtimeClasspath = getRuntimeClasspath(m);
    assertThat(runtimeClasspath).containsExactly(libraryRoot);

    VirtualFile[] compilationClasspath = getCompilationClasspath(m);
    assertEquals(1, compilationClasspath.length);
    VirtualFile[] production = getProductionCompileClasspath(m);
    assertThat(production).isEmpty();

    VirtualFile libraryClass = myFixture.createFile("lib/Test.class");
    assertFalse(m.getModuleWithDependenciesAndLibrariesScope(true).contains(libraryClass));
    assertFalse(m.getModuleWithDependenciesAndLibrariesScope(false).contains(libraryClass));

    assertTrue(m.getModuleRuntimeScope(true).contains(libraryClass));
    assertTrue(m.getModuleRuntimeScope(false).contains(libraryClass));
  }

  @Test
  public void testProvidedModuleDependency() {
    Module moduleA = heavyFixture.get().createModule(Path.of("a.iml"));
    addDependentModule(moduleA, DependencyScope.PROVIDED);
    VirtualFile[] runtimeClasspath = getRuntimeClasspath(moduleA);
    assertThat(runtimeClasspath).isEmpty();
    VirtualFile[] compilationClasspath = getCompilationClasspath(moduleA);
    assertEquals(1, compilationClasspath.length);
  }

  @Test
  public void testProvidedLibraryDependency() {
    Module m = heavyFixture.get().createModule(Path.of("a.iml"));
    VirtualFile libraryRoot = addLibrary(m, DependencyScope.PROVIDED);

    VirtualFile[] runtimeClasspath = getRuntimeClasspath(m);
    assertThat(runtimeClasspath).isEmpty();

    VirtualFile[] compilationClasspath = getCompilationClasspath(m);
    assertThat(compilationClasspath).containsExactly(libraryRoot);

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

  @Test
  public void testLibUnderModuleContent() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    VirtualFile lib = myFixture.findOrCreateDir("lib");
    PsiTestUtil.addContentRoot(fixture.getModule(), lib);

    VirtualFile file = createChildData(lib, "a.txt");
    addLibrary(fixture.getModule(), DependencyScope.COMPILE);
    assertTrue(fixture.getModule().getModuleWithDependenciesAndLibrariesScope(false).contains(file));
  }

  @Test
  public void testScopeEquality() {
    Module module = heavyFixture.get().createModule(Path.of("a.iml"));
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

  @Test
  public void testHonorExportsWhenCalculatingLibraryScope() throws IOException {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module a = fixture.createModule(Path.of("a.iml"));
    Module b = fixture.createModule(Path.of("b.iml"));
    Module c = fixture.createModule(Path.of("c.iml"));
    ModuleRootModificationUtil.addDependency(a, b, DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(b, c, DependencyScope.COMPILE, true);

    VirtualFile libFile1 = myFixture.createFile("lib1/a.txt", "");
    VirtualFile libFile2 = myFixture.createFile("lib2/a.txt", "");

    ModuleRootModificationUtil.addModuleLibrary(a, "l", Collections.singletonList(libFile1.getParent().getUrl()),
                                                Collections.emptyList(), Collections.emptyList(), DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addModuleLibrary(c, "l", Collections.singletonList(libFile2.getParent().getUrl()),
                                                Collections.emptyList(), Collections.emptyList(), DependencyScope.COMPILE, true);

    ReadAction.runBlocking(() -> {
      assertTrue(ResolveScopeManager.getElementResolveScope(fixture.getPsiManager().findFile(libFile1)).contains(libFile2));
      assertTrue(ResolveScopeManager.getElementResolveScope(fixture.getPsiManager().findFile(libFile2)).contains(libFile1));
    });
  }
}
