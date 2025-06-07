// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.artifact.impl.elements;

import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.artifact.JpsArtifactReference;
import org.jetbrains.jps.model.artifact.elements.*;
import org.jetbrains.jps.model.library.JpsLibraryReference;

@ApiStatus.Internal
public class JpsPackagingElementFactoryImpl extends JpsPackagingElementFactory {

  @Override
  public @NotNull JpsDirectoryCopyPackagingElement createDirectoryCopy(@NotNull String directoryPath) {
    return new JpsDirectoryCopyPackagingElementImpl(directoryPath);
  }

  @Override
  public JpsPackagingElement createParentDirectories(String relativeOutputPath, JpsPackagingElement element) {
    relativeOutputPath = Strings.trimStart(relativeOutputPath, "/");
    if (relativeOutputPath.isEmpty()) {
      return element;
    }
    int slash = relativeOutputPath.indexOf('/');
    if (slash == -1) slash = relativeOutputPath.length();
    String rootName = relativeOutputPath.substring(0, slash);
    String pathTail = relativeOutputPath.substring(slash);
    final JpsDirectoryPackagingElement root = createDirectory(rootName);
    final JpsCompositePackagingElement last = getOrCreateDirectoryOrArchive(root, pathTail, true);
    last.addChild(element);
    return root;
  }

  @Override
  public JpsCompositePackagingElement getOrCreateDirectory(@NotNull JpsCompositePackagingElement root, @NotNull String path) {
    return getOrCreateDirectoryOrArchive(root, path, true);
  }

  @Override
  public JpsCompositePackagingElement getOrCreateArchive(@NotNull JpsCompositePackagingElement root, @NotNull String path) {
    return getOrCreateDirectoryOrArchive(root, path, false);
  }

  private @NotNull JpsCompositePackagingElement getOrCreateDirectoryOrArchive(@NotNull JpsCompositePackagingElement root,
                                                                              @NotNull @NonNls String path, final boolean directory) {
    path = Strings.trimStart(Strings.trimEnd(path, "/"), "/");
    if (path.isEmpty()) {
      return root;
    }
    int index = path.lastIndexOf('/');
    String lastName = path.substring(index + 1);
    String parentPath = index != -1 ? path.substring(0, index) : "";

    final JpsCompositePackagingElement parent = getOrCreateDirectoryOrArchive(root, parentPath, true);
    final JpsCompositePackagingElement last = directory ? createDirectory(lastName) : createArchive(lastName);
    return parent.addChild(last);
  }

  @Override
  public @NotNull JpsFileCopyPackagingElement createFileCopy(@NotNull String filePath, @Nullable String outputFileName) {
    return new JpsFileCopyPackagingElementImpl(filePath, outputFileName);
  }

  @Override
  public @NotNull JpsExtractedDirectoryPackagingElement createExtractedDirectory(@NotNull String jarPath, @NotNull String pathInJar) {
    return new JpsExtractedDirectoryPackagingElementImpl(jarPath, pathInJar);
  }

  @Override
  public @NotNull JpsDirectoryPackagingElement createDirectory(@NotNull String directoryName) {
    return new JpsDirectoryPackagingElementImpl(directoryName);
  }

  @Override
  public @NotNull JpsArchivePackagingElement createArchive(@NotNull String archiveName) {
    return new JpsArchivePackagingElementImpl(archiveName);
  }

  @Override
  public @NotNull JpsArtifactRootElement createArtifactRoot() {
    return new JpsArtifactRootElementImpl();
  }

  @Override
  public @NotNull JpsLibraryFilesPackagingElement createLibraryElement(@NotNull JpsLibraryReference reference) {
    return new JpsLibraryFilesPackagingElementImpl(reference);
  }

  @Override
  public @NotNull JpsArtifactOutputPackagingElement createArtifactOutput(@NotNull JpsArtifactReference reference) {
    return new JpsArtifactOutputPackagingElementImpl(reference);
  }
}
