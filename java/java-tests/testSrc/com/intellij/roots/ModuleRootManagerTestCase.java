package com.intellij.roots;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ModuleTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.PathsList;

import java.io.IOException;

/**
 * @author nik
 */
public abstract class ModuleRootManagerTestCase extends ModuleTestCase {
  protected static void assertRoots(PathsList pathsList, VirtualFile... files) {
    assertOrderedEquals(pathsList.getRootDirs(), files);
  }

  @Override
  protected Sdk getTestProjectJdk() {
    final Sdk jdk = super.getTestProjectJdk();
    final SdkModificator modificator = jdk.getSdkModificator();
    VirtualFile rtJar = null;
    for (VirtualFile root : modificator.getRoots(OrderRootType.CLASSES)) {
      if (root.getName().equals("rt.jar")) {
        rtJar = root;
        break;
      }
    }
    assertNotNull("rt.jar not found in jdk: " + jdk, rtJar);
    modificator.removeAllRoots();
    modificator.addRoot(rtJar, OrderRootType.CLASSES);
    modificator.commitChanges();
    return jdk;
  }

  protected VirtualFile getRtJar() {
    return getTestProjectJdk().getRootProvider().getFiles(OrderRootType.CLASSES)[0];
  }

  protected VirtualFile getJDomJar() {
    return getJarFromLibDir("jdom.jar");
  }

  protected VirtualFile getJDomSources() {
    return getJarFromLibDir("src/jdom.zip");
  }


  protected VirtualFile getJarFromLibDir(final String name) {
    final VirtualFile file = getVirtualFile(PathManager.findFileInLibDirectory(name));
    assertNotNull(name + " not found", file);
    final VirtualFile jarFile = JarFileSystem.getInstance().getJarRootForLocalFile(file);
    assertNotNull(name + " is not jar", jarFile);
    return jarFile;
  }

  protected VirtualFile addSourceRoot(Module module, boolean testSource) throws IOException {
    final VirtualFile root = getVirtualFile(createTempDir(module.getName() + (testSource ? "Test" : "Prod") + "Src"));
    PsiTestUtil.addSourceContentToRoots(module, root, testSource);
    return root;
  }

  protected VirtualFile setModuleOutput(Module module, boolean test) throws IOException {
    final VirtualFile output = getVirtualFile(createTempDir(module.getName() + (test ? "Test" : "Prod") + "Output"));
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    final CompilerModuleExtension extension = model.getModuleExtension(CompilerModuleExtension.class);
    assertNotNull(extension);
    extension.inheritCompilerOutputPath(false);
    if (test) {
      extension.setCompilerOutputPathForTests(output);
    }
    else {
      extension.setCompilerOutputPath(output);
    }
    model.commit();
    return output;
  }

  protected void addLibraryDependency(Module module, Library dependency) {
    addLibraryDependency(module, dependency, DependencyScope.COMPILE, false);
  }

  protected void addLibraryDependency(Module module, Library dependency, DependencyScope scope, boolean exported) {
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    final LibraryOrderEntry entry = model.addLibraryEntry(dependency);
    entry.setScope(scope);
    entry.setExported(exported);
    model.commit();
  }

  protected void addModuleDependency(Module module, Module dependency) {
    addModuleDependency(module, dependency, DependencyScope.COMPILE, false);
  }

  protected void addModuleDependency(Module module, Module dependency, DependencyScope scope, boolean exported) {
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    final ModuleOrderEntry entry = model.addModuleOrderEntry(dependency);
    entry.setScope(scope);
    entry.setExported(exported);
    model.commit();
  }

  protected Library createLibrary(final String name, final VirtualFile classesRoot) {
    return createLibrary(name, classesRoot, null);
  }

  protected Library createLibrary(final String name, final VirtualFile classesRoot, final VirtualFile sourceRoot) {
    final Library library = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).createLibrary(name);
    final Library.ModifiableModel model = library.getModifiableModel();
    model.addRoot(classesRoot, OrderRootType.CLASSES);
    if (sourceRoot != null) {
      model.addRoot(sourceRoot, OrderRootType.SOURCES);
    }
    model.commit();
    return library;
  }

  protected Library createJDomLibrary() {
    return createLibrary("jdom", getJDomJar(), getJDomSources());
  }

  protected Library createAsmLibrary() {
    return createLibrary("asm", getAsmJar(), null);
  }

  protected VirtualFile getAsmJar() {
    return getJarFromLibDir("asm.jar");
  }
}
