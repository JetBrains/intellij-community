package com.intellij.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ModuleTestCase;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.util.PathsList;

import java.io.IOException;

/**
 * @author yole
 */
public class ModuleScopesTest extends ModuleTestCase {
  private LightTempDirTestFixtureImpl myFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture = new LightTempDirTestFixtureImpl();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myFixture.deleteAll();
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
    final Module moduleB = createModule("b.iml", StdModuleTypes.JAVA);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        VirtualFile rootB = myFixture.findOrCreateDir("b");
        VirtualFile outB = myFixture.findOrCreateDir("out");

        final ModifiableRootModel modelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
        modelA.addModuleOrderEntry(moduleB).setScope(scope);
        modelA.commit();

        final ModifiableRootModel modelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
        final ContentEntry contentEntry = modelB.addContentEntry(rootB);
        contentEntry.addSourceFolder(rootB, false);
        final CompilerModuleExtension extension = modelB.getModuleExtension(CompilerModuleExtension.class);
        extension.inheritCompilerOutputPath(false);
        extension.setCompilerOutputPath(outB);
        modelB.commit();
      }
    });

    return moduleB;
  }

  public void testTestOnlyLibraryDependency() throws IOException {
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

  public void testRuntimeModuleDependency() throws IOException {
    Module moduleA = createModule("a.iml", StdModuleTypes.JAVA);
    addDependentModule(moduleA, DependencyScope.RUNTIME);
    final VirtualFile[] runtimeClasspath = getRuntimeClasspath(moduleA);
    assertEquals(1, runtimeClasspath.length);
    final VirtualFile[] compilationClasspath = getCompilationClasspath(moduleA);
    assertEquals(1, compilationClasspath.length);
    VirtualFile[] production = getProductionCompileClasspath(moduleA);
    assertEmpty(production);
  }

  public void testRuntimeLibraryDependency() throws IOException {
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

  public void testProvidedModuleDependency() throws IOException {
    Module moduleA = createModule("a.iml", StdModuleTypes.JAVA);
    addDependentModule(moduleA, DependencyScope.PROVIDED);
    VirtualFile[] runtimeClasspath = getRuntimeClasspath(moduleA);
    assertEmpty(runtimeClasspath);
    final VirtualFile[] compilationClasspath = getCompilationClasspath(moduleA);
    assertEquals(1, compilationClasspath.length);
  }

  public void testProvidedLibraryDependency() throws IOException {
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
    return ModuleRootManager.getInstance(moduleA).orderEntries().productionOnly().compileOnly().recursively().exportedOnly().getClassesRoots();
  }

  private static VirtualFile[] getCompilationClasspath(Module m) {
    return ModuleRootManager.getInstance(m).orderEntries().recursively().exportedOnly().getClassesRoots();
  }

  private VirtualFile addLibrary(final Module m, final DependencyScope scope) {
    final VirtualFile libraryRoot = myFixture.findOrCreateDir("lib");

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final ModifiableRootModel model = ModuleRootManager.getInstance(m).getModifiableModel();
        final Library library = model.getModuleLibraryTable().createLibrary("l");
        final Library.ModifiableModel libraryModel = library.getModifiableModel();
        libraryModel.addRoot(libraryRoot, OrderRootType.CLASSES);
        libraryModel.commit();

        model.findLibraryOrderEntry(library).setScope(scope);
        model.commit();
      }
    });

    return libraryRoot;
  }

  public void testLibUnderModuleContent() throws IOException {
    VirtualFile lib = myFixture.findOrCreateDir("lib");
    ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
    model.addContentEntry(lib);
    model.commit();

    VirtualFile file = lib.createChildData(this, "a.txt");
    addLibrary(myModule, DependencyScope.COMPILE);
    assertTrue(myModule.getModuleWithDependenciesAndLibrariesScope(false).contains(file));
  }
}
