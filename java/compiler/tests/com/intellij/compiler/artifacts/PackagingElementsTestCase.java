package com.intellij.compiler.artifacts;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author nik
 */
public abstract class PackagingElementsTestCase extends ArtifactsTestCase {
  protected Artifact addArtifact(PackagingElementBuilder builder) {
    return addArtifact("a", builder);
  }

  protected Artifact addArtifact(final String name, PackagingElementBuilder builder) {
    return addArtifact(name, builder.build());
  }

  protected static void assertLayout(Artifact artifact, String expected) {
    assertLayout(artifact.getRootElement(), expected);
  }

  protected static void assertLayout(PackagingElement element, String expected) {
    ArtifactsTestUtil.assertLayout(element, expected);
  }

  protected String getProjectBasePath() {
    return getBaseDir().getPath();
  }

  protected VirtualFile getBaseDir() {
    final VirtualFile baseDir = myProject.getBaseDir();
    assertNotNull(baseDir);
    return baseDir;
  }

  protected static PackagingElementFactory getFactory() {
    return PackagingElementFactory.getInstance();
  }

  protected PackagingElementBuilder root() {
    return new PackagingElementBuilder(getFactory().createArtifactRootElement(), null);
  }

  protected PackagingElementBuilder archive(String name) {
    return new PackagingElementBuilder(getFactory().createArchive(name), null);
  }

  protected PackagingElementBuilder dir(String name) {
    return new PackagingElementBuilder(getFactory().createDirectory(name), null);
  }

  protected VirtualFile createFile(final String path) {
    return createFile(path, "");
  }

  protected VirtualFile createFile(final String path, final String text) {
    return VfsTestUtil.createFile(getBaseDir(), path, text);
  }

  protected VirtualFile createDir(final String path) {
    return VfsTestUtil.createDir(getBaseDir(), path);
  }

  protected static VirtualFile getJDomJar() {
    return getJarFromLibDirectory("jdom.jar");
  }

  protected static String getLocalJarPath(VirtualFile jarEntry) {
    return PathUtil.getLocalFile(jarEntry).getPath();
  }

  protected static String getJUnitJarPath() {
    return getLocalJarPath(getJarFromLibDirectory("junit.jar"));
  }

  private static VirtualFile getJarFromLibDirectory(final String relativePath) {
    final File file = PathManager.findFileInLibDirectory(relativePath);
    final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    assertNotNull(file.getAbsolutePath() + " not found", virtualFile);
    final VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile);
    assertNotNull(jarRoot);
    return jarRoot;
  }

  protected Library addProjectLibrary(final @Nullable Module module, final String name, final VirtualFile... jars) {
    return addProjectLibrary(module, name, DependencyScope.COMPILE, jars);
  }

  protected Library addProjectLibrary(final @Nullable Module module, final String name, final DependencyScope scope,
                                      final VirtualFile... jars) {
    return new WriteAction<Library>() {
      @Override
      protected void run(final Result<Library> result) {
        final Library library = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).createLibrary(name);
        final Library.ModifiableModel libraryModel = library.getModifiableModel();
        for (VirtualFile jar : jars) {
          libraryModel.addRoot(jar, OrderRootType.CLASSES);
        }
        libraryModel.commit();
        if (module != null) {
          ModuleRootModificationUtil.addDependency(module, library, scope, false);
        }
        result.setResult(library);
      }
    }.execute().getResultObject();
  }

  protected static void addModuleLibrary(final Module module, final VirtualFile jar) {
    ModuleRootModificationUtil.addModuleLibrary(module, jar.getUrl());
  }

  protected static void addModuleDependency(final Module module, final Module dependency) {
    ModuleRootModificationUtil.addDependency(module, dependency);
  }

  protected class PackagingElementBuilder {
    private final CompositePackagingElement<?> myElement;
    private final PackagingElementBuilder myParent;

    private PackagingElementBuilder(CompositePackagingElement<?> element, PackagingElementBuilder parent) {
      myElement = element;
      myParent = parent;
    }

    public CompositePackagingElement<?> build() {
      PackagingElementBuilder builder = this;
      while (builder.myParent != null) {
        builder = builder.myParent;
      }
      return builder.myElement;
    }

    public PackagingElementBuilder file(VirtualFile file) {
      return file(file.getPath());
    }

    public PackagingElementBuilder file(String path) {
      myElement.addOrFindChild(getFactory().createFileCopyWithParentDirectories(path, "/"));
      return this;
    }

    public PackagingElementBuilder dirCopy(VirtualFile dir) {
      return dirCopy(dir.getPath());
    }

    public PackagingElementBuilder dirCopy(String path) {
      myElement.addOrFindChild(getFactory().createDirectoryCopyWithParentDirectories(path, "/"));
      return this;
    }

    public PackagingElementBuilder extractedDir(String jarPath, String pathInJar) {
      myElement.addOrFindChild(getFactory().createExtractedDirectoryWithParentDirectories(jarPath, pathInJar, "/"));
      return this;
    }

    public PackagingElementBuilder module(Module module) {
      myElement.addOrFindChild(getFactory().createModuleOutput(module));
      return this;
    }

    public PackagingElementBuilder lib(Library library) {
      myElement.addOrFindChildren(getFactory().createLibraryElements(library));
      return this;
    }

    public PackagingElementBuilder artifact(Artifact artifact) {
      myElement.addOrFindChild(getFactory().createArtifactElement(artifact, myProject));
      return this;
    }

    public PackagingElementBuilder archive(String name) {
      final CompositePackagingElement<?> archive = getFactory().createArchive(name);
      return new PackagingElementBuilder(myElement.addOrFindChild(archive), this);
    }

    public PackagingElementBuilder dir(String name) {
      return new PackagingElementBuilder(myElement.addOrFindChild(getFactory().createDirectory(name)), this);
    }

    public PackagingElementBuilder add(PackagingElement<?> element) {
      myElement.addOrFindChild(element);
      return this;
    }

    public PackagingElementBuilder end() {
      return myParent;
    }
  }
}
