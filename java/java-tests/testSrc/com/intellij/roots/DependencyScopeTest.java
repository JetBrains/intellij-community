package com.intellij.roots;

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
public class DependencyScopeTest extends ModuleTestCase {
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

  public void testTestOnlyModuleDependency() throws Exception {
    Module moduleA = createModule("a.iml", StdModuleTypes.JAVA);
    Module moduleB = addDependentModule(moduleA, DependencyScope.TEST);

    VirtualFile classB = myFixture.createFile("b/Test.java", "public class Test { }");
    assertTrue(moduleA.getModuleWithDependenciesAndLibrariesScope(true).contains(classB));
    assertFalse(moduleA.getModuleWithDependenciesAndLibrariesScope(false).contains(classB));
    assertFalse(moduleA.getModuleWithDependenciesAndLibrariesScope(false).isSearchInModuleContent(moduleB));

    final VirtualFile[] compilationClasspath = ModuleRootManager.getInstance(moduleA).getFiles(OrderRootType.COMPILATION_CLASSES);
    assertEquals(1, compilationClasspath.length);
    final VirtualFile[] productionCompilationClasspath = ModuleRootManager.getInstance(moduleA).getFiles(OrderRootType.PRODUCTION_COMPILATION_CLASSES);
    assertEquals(0, productionCompilationClasspath.length);

    final PathsList pathsList = ProjectRootsTraversing.collectRoots(moduleA, ProjectClasspathTraversing.FULL_CLASSPATH_RECURSIVE);
    assertEquals(1, pathsList.getPathList().size());
    final PathsList pathsListWithoutTests = ProjectRootsTraversing.collectRoots(moduleA, ProjectClasspathTraversing.FULL_CLASSPATH_WITHOUT_TESTS);
    assertEquals(0, pathsListWithoutTests.getPathList().size());
  }

  private Module addDependentModule(Module moduleA, final DependencyScope scope) {
    Module moduleB = createModule("b.iml", StdModuleTypes.JAVA);

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
    return moduleB;
  }

  public void testTestOnlyLibraryDependency() throws IOException {
    Module m = createModule("a.iml", StdModuleTypes.JAVA);
    addLibrary(m, DependencyScope.TEST);
    VirtualFile libraryClass = myFixture.createFile("lib/Test.java", "public class Test { }");

    assertTrue(m.getModuleWithDependenciesAndLibrariesScope(true).contains(libraryClass));
    assertFalse(m.getModuleWithDependenciesAndLibrariesScope(false).contains(libraryClass));

    final VirtualFile[] compilationClasspath = ModuleRootManager.getInstance(m).getFiles(OrderRootType.COMPILATION_CLASSES);
    assertEquals(1, compilationClasspath.length);
    final VirtualFile[] productionCompilationClasspath = ModuleRootManager.getInstance(m).getFiles(OrderRootType.PRODUCTION_COMPILATION_CLASSES);
    assertEquals(0, productionCompilationClasspath.length);
  }

  public void testRuntimeModuleDependency() throws IOException {
    Module moduleA = createModule("a.iml", StdModuleTypes.JAVA);
    addDependentModule(moduleA, DependencyScope.RUNTIME);
    final VirtualFile[] runtimeClasspath = ModuleRootManager.getInstance(moduleA).getFiles(OrderRootType.CLASSES_AND_OUTPUT);
    assertEquals(1, runtimeClasspath.length);
    final VirtualFile[] compilationClasspath = ModuleRootManager.getInstance(moduleA).getFiles(OrderRootType.COMPILATION_CLASSES);
    assertEquals(1, compilationClasspath.length);
    VirtualFile[] production = ModuleRootManager.getInstance(moduleA).getFiles(OrderRootType.PRODUCTION_COMPILATION_CLASSES);
    assertEquals(0, production.length);
  }

  public void testRuntimeLibraryDependency() throws IOException {
    Module m = createModule("a.iml", StdModuleTypes.JAVA);
    VirtualFile libraryRoot = addLibrary(m, DependencyScope.RUNTIME);

    final VirtualFile[] runtimeClasspath = ModuleRootManager.getInstance(m).getFiles(OrderRootType.CLASSES_AND_OUTPUT);
    assertEquals(1, runtimeClasspath.length);
    assertEquals(libraryRoot, runtimeClasspath [0]);

    final VirtualFile[] compilationClasspath = ModuleRootManager.getInstance(m).getFiles(OrderRootType.COMPILATION_CLASSES);
    assertEquals(1, compilationClasspath.length);
    VirtualFile[] production = ModuleRootManager.getInstance(m).getFiles(OrderRootType.PRODUCTION_COMPILATION_CLASSES);
    assertEquals(0, production.length);

    VirtualFile libraryClass = myFixture.createFile("lib/Test.java", "public class Test { }");
    assertTrue(m.getModuleWithDependenciesAndLibrariesScope(true).contains(libraryClass));
    assertFalse(m.getModuleWithDependenciesAndLibrariesScope(false).contains(libraryClass));
  }

  public void testProvidedModuleDependency() throws IOException {
    Module moduleA = createModule("a.iml", StdModuleTypes.JAVA);
    addDependentModule(moduleA, DependencyScope.PROVIDED);
    final VirtualFile[] runtimeClasspath = ModuleRootManager.getInstance(moduleA).getFiles(OrderRootType.CLASSES_AND_OUTPUT);
    assertEquals(0, runtimeClasspath.length);
    final VirtualFile[] compilationClasspath = ModuleRootManager.getInstance(moduleA).getFiles(OrderRootType.COMPILATION_CLASSES);
    assertEquals(1, compilationClasspath.length);
  }

  public void testProvidedLibraryDependency() throws IOException {
    Module m = createModule("a.iml", StdModuleTypes.JAVA);
    VirtualFile libraryRoot = addLibrary(m, DependencyScope.PROVIDED);

    final VirtualFile[] runtimeClasspath = ModuleRootManager.getInstance(m).getFiles(OrderRootType.CLASSES_AND_OUTPUT);
    assertEquals(0, runtimeClasspath.length);

    final VirtualFile[] compilationClasspath = ModuleRootManager.getInstance(m).getFiles(OrderRootType.COMPILATION_CLASSES);
    assertEquals(1, compilationClasspath.length);
    assertEquals(libraryRoot, compilationClasspath [0]);

    VirtualFile libraryClass = myFixture.createFile("lib/Test.java", "public class Test { }");
    assertTrue(m.getModuleWithDependenciesAndLibrariesScope(true).contains(libraryClass));
    assertTrue(m.getModuleWithDependenciesAndLibrariesScope(false).contains(libraryClass));
  }

  private VirtualFile addLibrary(Module m, final DependencyScope scope) {
    VirtualFile libraryRoot = myFixture.findOrCreateDir("lib");

    final ModifiableRootModel model = ModuleRootManager.getInstance(m).getModifiableModel();
    final Library library = model.getModuleLibraryTable().createLibrary("l");
    final Library.ModifiableModel libraryModel = library.getModifiableModel();
    libraryModel.addRoot(libraryRoot, OrderRootType.CLASSES);
    libraryModel.commit();

    model.findLibraryOrderEntry(library).setScope(scope);
    model.commit();
    return libraryRoot;
  }
}
