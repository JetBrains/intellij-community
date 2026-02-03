// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.LibraryTypeService;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.libraries.ui.LibraryRootsComponentDescriptor;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.libraries.ui.impl.RootDetectionUtil;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.eel.EelDescriptor;
import com.intellij.platform.eel.provider.EelProviderUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class LibraryTypeServiceImpl extends LibraryTypeService {
  private static final String DEFAULT_LIBRARY_NAME = "Unnamed";

  private static final Logger LOG = Logger.getInstance(LibraryTypeServiceImpl.class);

  @Override
  public NewLibraryConfiguration createLibraryFromFiles(@NotNull LibraryRootsComponentDescriptor descriptor,
                                                        @NotNull JComponent parentComponent,
                                                        @Nullable VirtualFile contextDirectory,
                                                        LibraryType<?> type,
                                                        final Project project) {
    final FileChooserDescriptor chooserDescriptor = descriptor.createAttachFilesChooserDescriptor(null);
    chooserDescriptor.setTitle(ProjectBundle.message("chooser.title.select.library.files"));
    final VirtualFile[] rootCandidates = FileChooser.chooseFiles(chooserDescriptor, parentComponent, project, contextDirectory);
    if (rootCandidates.length == 0) {
      return null;
    }
    var compatibleRootCandidates = filterEnvironmentCompatibleRoots(project, rootCandidates);
    if (compatibleRootCandidates.isEmpty()) {
      return null;
    }
    final List<OrderRoot> roots = RootDetectionUtil.detectRoots(compatibleRootCandidates, parentComponent, project, descriptor);
    if (roots.isEmpty()) return null;
    String name = suggestLibraryName(roots);
    return doCreate(type, name, roots);
  }

  private static @NotNull Collection<VirtualFile> filterEnvironmentCompatibleRoots(@Nullable Project project,
                                                                                   VirtualFile @NotNull [] rootCandidates) {
    if (project == null || project.isDefault()) {
      return Arrays.asList(rootCandidates);
    }
    EelDescriptor eelDescriptor = EelProviderUtil.getEelDescriptor(project);
    var candidatesByEnvironmentCompatibility =
      ContainerUtil.groupBy(Arrays.asList(rootCandidates), root -> {
        Path path = getPathOrNull(root);
        return path == null || EelProviderUtil.getEelDescriptor(path).equals(eelDescriptor);
      });
    if (!candidatesByEnvironmentCompatibility.get(false).isEmpty()) {
      LOG.info("Some roots are not environment-compatible: " + candidatesByEnvironmentCompatibility.get(false));
    }
    return candidatesByEnvironmentCompatibility.get(true);
  }

  private static @Nullable Path getPathOrNull(@NotNull VirtualFile root) {
    // getLocalFile() method name is misleading in the context of environments
    // it just returns the file in the file system if the root is an archive
    VirtualFile file = VfsUtil.getLocalFile(root);
    try {
      return file.toNioPath();
    }
    catch (UnsupportedOperationException ignored) {
      return null;
    }
  }

  private static @NotNull <P extends LibraryProperties<?>> NewLibraryConfiguration doCreate(final LibraryType<P> type, final String name, final List<? extends OrderRoot> roots) {
    return new NewLibraryConfiguration(name, type, type != null ? type.getKind().createDefaultProperties() : null) {
      @Override
      public void addRoots(@NotNull LibraryEditor editor) {
        editor.addRoots(roots);
      }
    };
  }

  public static @NotNull String suggestLibraryName(VirtualFile @NotNull [] classesRoots) {
    if (classesRoots.length >= 1) {
      return FileUtilRt.getNameWithoutExtension(PathUtil.getFileName(classesRoots[0].getPath()));
    }
    return DEFAULT_LIBRARY_NAME;
  }

  public static @NotNull String suggestLibraryName(@NotNull List<? extends OrderRoot> roots) {
    if (!roots.isEmpty()) {
      return FileUtilRt.getNameWithoutExtension(PathUtil.getFileName(roots.get(0).getFile().getPath()));
    }
    return DEFAULT_LIBRARY_NAME;
  }
}
