package com.intellij.compiler.artifacts;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.impl.elements.*;

import java.io.IOException;

/**
 * @author nik
 */
public abstract class PackagingElementsTestCase extends ArtifactsTestCase {

  protected void assertLayout(PackagingElement element, String expected) {
    assertEquals(expected, printToString(element, 0));
  }

  private static String printToString(PackagingElement element, int level) {
    StringBuilder builder = new StringBuilder(StringUtil.repeatSymbol(' ', level));
    if (element instanceof ArchivePackagingElement) {
      builder.append(((ArchivePackagingElement)element).getArchiveFileName());
    }
    else if (element instanceof DirectoryPackagingElement) {
      builder.append(((DirectoryPackagingElement)element).getDirectoryName()).append("/");
    }
    else if (element instanceof ArtifactPackagingElement) {
      builder.append("artifact:").append(((ArtifactPackagingElement)element).getArtifactName());
    }
    else if (element instanceof LibraryPackagingElement) {
      builder.append("lib:").append(((LibraryPackagingElement)element).getName());
    }
    else if (element instanceof ModuleOutputPackagingElement) {
      builder.append("module:").append(((ModuleOutputPackagingElement)element).getModuleName());
    }
    else if (element instanceof FileCopyPackagingElement) {
      builder.append("file:").append(((FileCopyPackagingElement)element).getFilePath());
    }
    builder.append("\n");
    if (element instanceof CompositePackagingElement) {
      for (PackagingElement<?> child : ((CompositePackagingElement<?>)element).getChildren()) {
        builder.append(printToString(child, level + 1));
      }
    }
    return builder.toString();
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

  protected VirtualFile createFile(final String name) throws IOException {
    return new WriteAction<VirtualFile>() {
      protected void run(final Result<VirtualFile> result) {
        try {
          result.setResult(myProject.getBaseDir().createChildData(this, name));
        }
        catch (IOException e) {
          throw new AssertionError(e);
        }
      }
    }.execute().getResultObject();
  }

  protected VirtualFile getJDomJar() {
    return JarFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(PathManager.getLibPath()) + "/jdom.jar" +
                                                      JarFileSystem.JAR_SEPARATOR);
  }

  protected Library addProjectLibrary(Module module, String name, VirtualFile... jars) {
    final Library library = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).createLibrary(name);
    final Library.ModifiableModel libraryModel = library.getModifiableModel();
    for (VirtualFile jar : jars) {
      libraryModel.addRoot(jar, OrderRootType.CLASSES);
    }
    libraryModel.commit();
    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
    rootModel.addLibraryEntry(library);
    rootModel.commit();
    return library;
  }

  protected Library addModuleLibrary(Module module, VirtualFile... jars) {
    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
    final Library library = rootModel.getModuleLibraryTable().createLibrary();
    final Library.ModifiableModel libraryModel = library.getModifiableModel();
    for (VirtualFile jar : jars) {
      libraryModel.addRoot(jar, OrderRootType.CLASSES);
    }
    libraryModel.commit();
    rootModel.commit();
    return library;
  }

  protected class PackagingElementBuilder {
    private CompositePackagingElement<?> myElement;
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

    public PackagingElementBuilder file(String path) {
      myElement.addOrFindChild(getFactory().createFileCopyWithParentDirectories(path, "/"));
      return this;
    }

    public PackagingElementBuilder module(Module module) {
      myElement.addOrFindChild(getFactory().createModuleOutput(module.getName()));
      return this;
    }

    public PackagingElementBuilder lib(Library library) {
      myElement.addOrFindChildren(getFactory().createLibraryElements(library));
      return this;
    }

    public PackagingElementBuilder artifact(Artifact artifact) {
      myElement.addOrFindChild(getFactory().createArtifactElement(artifact));
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
