// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.AttachRootButtonDescriptor;
import com.intellij.openapi.roots.libraries.ui.LibraryRootsComponentDescriptor;
import com.intellij.openapi.roots.libraries.ui.OrderRootTypePresentation;
import com.intellij.openapi.roots.libraries.ui.RootDetector;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.DefaultLibraryRootsComponentDescriptor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;

import java.io.File;
import java.util.Collections;
import java.util.List;

class RepositoryLibraryRootsComponentDescriptor extends LibraryRootsComponentDescriptor {
  private static final Logger LOG = Logger.getInstance(RepositoryLibraryRootsComponentDescriptor.class);

  @Nullable
  @Override
  public OrderRootTypePresentation getRootTypePresentation(@NotNull OrderRootType type) {
    return DefaultLibraryRootsComponentDescriptor.getDefaultPresentation(type);
  }

  @NotNull
  @Override
  public List<? extends RootDetector> getRootDetectors() {
    return Collections.singletonList(DefaultLibraryRootsComponentDescriptor.createAnnotationsRootDetector());
  }

  @NotNull
  @Override
  public FileChooserDescriptor createAttachFilesChooserDescriptor(@Nullable String libraryName) {
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setTitle(JavaUiBundle.message("chooser.title.attach.external.annotations"));
    descriptor.setDescription(JavaUiBundle.message("chooser.description.select.directory.where.external.annotations.are.located"));
    return descriptor;
  }

  @Override
  public @NlsActions.ActionText String getAttachFilesActionName() {
    return JavaUiBundle.message("repository.library.root.action.attach.annotations.text");
  }

  @NotNull
  @Override
  public List<? extends AttachRootButtonDescriptor> createAttachButtons() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public RootRemovalHandler createRootRemovalHandler() {
    return new RootRemovalHandler() {
      @Override
      public void onRootRemoved(@NotNull String rootUrl, @NotNull OrderRootType rootType, @NotNull LibraryEditor libraryEditor) {
        if (rootType == OrderRootType.CLASSES) {
          String coordinates = getMavenCoordinates(rootUrl);
          if (coordinates != null) {
            RepositoryLibraryProperties properties = (RepositoryLibraryProperties)libraryEditor.getProperties();
            properties.setExcludedDependencies(ContainerUtil.append(properties.getExcludedDependencies(), coordinates));
          }
          else {
            LOG.warn("Cannot determine Maven coordinates for removed library root " + rootUrl);
          }
        }
      }
    };
  }

  @Nullable
  private static String getMavenCoordinates(@NotNull String jarUrl) {
    File jarFile = new File(PathUtil.getLocalPath(VfsUtilCore.urlToPath(jarUrl)));
    if (jarFile.getParentFile() == null) return null;
    File artifactDir = jarFile.getParentFile().getParentFile();
    if (artifactDir == null) return null;
    File localRepoRoot = JarRepositoryManager.getLocalRepositoryPath();
    if (!FileUtil.isAncestor(localRepoRoot, artifactDir, true)) return null;
    String relativePath = FileUtil.getRelativePath(localRepoRoot, artifactDir.getParentFile());
    if (relativePath == null) return null;
    return relativePath.replace(File.separatorChar, '.') + ":" + artifactDir.getName();
  }
}
