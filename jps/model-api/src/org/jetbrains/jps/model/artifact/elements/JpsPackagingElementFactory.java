// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.artifact.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.artifact.JpsArtifactReference;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.service.JpsServiceManager;

public abstract class JpsPackagingElementFactory {
  public abstract JpsCompositePackagingElement getOrCreateDirectory(@NotNull JpsCompositePackagingElement root, @NotNull String path);

  public abstract JpsCompositePackagingElement getOrCreateArchive(@NotNull JpsCompositePackagingElement root, @NotNull String path);

  public static JpsPackagingElementFactory getInstance() {
    return JpsServiceManager.getInstance().getService(JpsPackagingElementFactory.class);
  }

  public abstract @NotNull JpsDirectoryCopyPackagingElement createDirectoryCopy(@NotNull String directoryPath);

  public abstract JpsPackagingElement createParentDirectories(String path, JpsPackagingElement element);

  public abstract @NotNull JpsFileCopyPackagingElement createFileCopy(@NotNull String filePath, @Nullable String outputFileName);

  public abstract @NotNull JpsExtractedDirectoryPackagingElement createExtractedDirectory(@NotNull String jarPath, @NotNull String pathInJar);

  public abstract @NotNull JpsDirectoryPackagingElement createDirectory(@NotNull String directoryName);

  public abstract @NotNull JpsArchivePackagingElement createArchive(@NotNull String archiveName);

  public abstract @NotNull JpsArtifactRootElement createArtifactRoot();

  public abstract @NotNull JpsLibraryFilesPackagingElement createLibraryElement(@NotNull JpsLibraryReference reference);

  public abstract @NotNull JpsArtifactOutputPackagingElement createArtifactOutput(@NotNull JpsArtifactReference reference);

}
