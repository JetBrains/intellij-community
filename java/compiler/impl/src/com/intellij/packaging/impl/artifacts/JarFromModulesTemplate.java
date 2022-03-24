// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.artifacts;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ArtifactTemplate;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.elements.LibraryPackagingElement;
import com.intellij.packaging.impl.elements.ManifestFileUtil;
import com.intellij.packaging.impl.elements.ProductionModuleOutputElementType;
import com.intellij.packaging.impl.elements.TestModuleOutputElementType;
import com.intellij.util.CommonProcessors;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

public final class JarFromModulesTemplate extends ArtifactTemplate {
  private static final Logger LOG = Logger.getInstance(JarFromModulesTemplate.class);

  private final PackagingElementResolvingContext myContext;

  public JarFromModulesTemplate(PackagingElementResolvingContext context) {
    myContext = context;
  }

  @Override
  public NewArtifactConfiguration createArtifact() {
    JarArtifactFromModulesDialog dialog = new JarArtifactFromModulesDialog(myContext);
    if (!dialog.showAndGet()) {
      return null;
    }

    return doCreateArtifact(dialog.getSelectedModules(), dialog.getMainClassName(), dialog.getDirectoryForManifest(),
                            dialog.isExtractLibrariesToJar(), dialog.isIncludeTests());
  }

  @Nullable
  public NewArtifactConfiguration doCreateArtifact(final Module[] modules, final String mainClassName,
                                                   final String directoryForManifest,
                                                   final boolean extractLibrariesToJar,
                                                   final boolean includeTests) {
    VirtualFile manifestFile = null;
    final Project project = myContext.getProject();
    if (mainClassName != null && !mainClassName.isEmpty() || !extractLibrariesToJar) {
      final VirtualFile directory;
      try {
        directory = ApplicationManager.getApplication().runWriteAction(
          (ThrowableComputable<VirtualFile, IOException>)() -> VfsUtil.createDirectoryIfMissing(directoryForManifest));
      }
      catch (IOException e) {
        LOG.info(e);
        Messages.showErrorDialog(project, JavaCompilerBundle.message("cannot.create.directory.0.1", directoryForManifest, e.getMessage()),
                                 CommonBundle.getErrorTitle());
        return null;
      }
      if (directory == null) return null;

      manifestFile = ManifestFileUtil.createManifestFile(directory, project);
      if (manifestFile == null) {
        return null;
      }
      ManifestFileUtil.updateManifest(manifestFile, mainClassName, null, true);
    }

    String name = modules.length == 1 ? modules[0].getName() : project.getName();

    final PackagingElementFactory factory = PackagingElementFactory.getInstance();
    final CompositePackagingElement<?> archive = factory.createArchive(ArtifactUtil.suggestArtifactFileName(name) + ".jar");

    OrderEnumerator orderEnumerator = ProjectRootManager.getInstance(project).orderEntries(Arrays.asList(modules));

    final Set<Library> libraries = new HashSet<>();
    if (!includeTests) {
      orderEnumerator = orderEnumerator.productionOnly();
    }
    final ModulesProvider modulesProvider = myContext.getModulesProvider();
    final OrderEnumerator enumerator = orderEnumerator.using(modulesProvider).withoutSdk().runtimeOnly().recursively();
    enumerator.forEachLibrary(new CommonProcessors.CollectProcessor<>(libraries));
    enumerator.forEachModule(module -> {
      if (ProductionModuleOutputElementType.ELEMENT_TYPE.isSuitableModule(modulesProvider, module)) {
        archive.addOrFindChild(factory.createModuleOutput(module));
      }
      if (includeTests && TestModuleOutputElementType.ELEMENT_TYPE.isSuitableModule(modulesProvider, module)) {
        archive.addOrFindChild(factory.createTestModuleOutput(module));
      }
      return true;
    });

    final JarArtifactType jarArtifactType = JarArtifactType.getInstance();
    if (manifestFile != null && !manifestFile.equals(ManifestFileUtil.findManifestFile(archive, myContext, jarArtifactType))) {
      archive.addFirstChild(factory.createFileCopyWithParentDirectories(manifestFile.getPath(), ManifestFileUtil.MANIFEST_DIR_NAME));
    }

    final String artifactName = name + ":jar";
    if (extractLibrariesToJar) {
      addExtractedLibrariesToJar(archive, factory, libraries);
      return new NewArtifactConfiguration(archive, artifactName, jarArtifactType);
    }
    else {
      final ArtifactRootElement<?> root = factory.createArtifactRootElement();
      List<String> classpath = new ArrayList<>();
      root.addOrFindChild(archive);
      addLibraries(libraries, root, archive, classpath);
      ManifestFileUtil.updateManifest(manifestFile, mainClassName, classpath, true);
      return new NewArtifactConfiguration(root, artifactName, PlainArtifactType.getInstance());
    }
  }

  private void addLibraries(Set<? extends Library> libraries, ArtifactRootElement<?> root, CompositePackagingElement<?> archive,
                            List<? super String> classpath) {
    PackagingElementFactory factory = PackagingElementFactory.getInstance();
    for (Library library : libraries) {
      if (LibraryPackagingElement.getKindForLibrary(library).containsDirectoriesWithClasses()) {
        for (VirtualFile classesRoot : library.getFiles(OrderRootType.CLASSES)) {
          if (classesRoot.isInLocalFileSystem()) {
            archive.addOrFindChild(factory.createDirectoryCopyWithParentDirectories(classesRoot.getPath(), "/"));
          }
          else {
            final PackagingElement<?> child = factory.createFileCopyWithParentDirectories(VfsUtil.getLocalFile(classesRoot).getPath(), "/");
            root.addOrFindChild(child);
            classpath.addAll(ManifestFileUtil.getClasspathForElements(Collections.singletonList(child), myContext, PlainArtifactType.getInstance()));
          }
        }

      }
      else {
        final List<? extends PackagingElement<?>> children = factory.createLibraryElements(library);
        classpath.addAll(ManifestFileUtil.getClasspathForElements(children, myContext, PlainArtifactType.getInstance()));
        root.addOrFindChildren(children);
      }
    }
  }

  private static void addExtractedLibrariesToJar(CompositePackagingElement<?> archive, PackagingElementFactory factory, Set<? extends Library> libraries) {
    for (Library library : libraries) {
      if (LibraryPackagingElement.getKindForLibrary(library).containsJarFiles()) {
        for (VirtualFile classesRoot : library.getFiles(OrderRootType.CLASSES)) {
          if (classesRoot.isInLocalFileSystem()) {
            archive.addOrFindChild(factory.createDirectoryCopyWithParentDirectories(classesRoot.getPath(), "/"));
          }
          else {
            archive.addOrFindChild(factory.createExtractedDirectory(classesRoot));
          }
        }

      }
      else {
        archive.addOrFindChildren(factory.createLibraryElements(library));
      }
    }
  }

  @Override
  public @Nls String getPresentableName() {
    return JavaCompilerBundle.message("jar.from.modules.presentable.name");
  }
}
