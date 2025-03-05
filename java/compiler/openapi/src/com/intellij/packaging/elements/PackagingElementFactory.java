// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.elements;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactPointer;
import com.intellij.platform.workspace.jps.entities.LibraryEntity;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class PackagingElementFactory {

  public static PackagingElementFactory getInstance() {
    return ApplicationManager.getApplication().getService(PackagingElementFactory.class);
  }

  public abstract @NotNull ArtifactRootElement<?> createArtifactRootElement();

  public abstract @NotNull CompositePackagingElement<?> createDirectory(@NotNull @NonNls String directoryName);

  public abstract @NotNull CompositePackagingElement<?> createArchive(@NotNull @NonNls String archiveFileName);

  public abstract PackagingElement<?> createFileCopy(@NotNull @NonNls String filePath, @Nullable @NonNls String outputFileName);

  public abstract @NotNull PackagingElement<?> createModuleOutput(@NotNull @NonNls String moduleName, @NotNull Project project);

  public abstract @NotNull PackagingElement<?> createModuleOutput(@NotNull Module module);

  public abstract @NotNull PackagingElement<?> createModuleSource(@NotNull Module module);

  public abstract @NotNull PackagingElement<?> createTestModuleOutput(@NotNull String moduleName, @NotNull Project project);

  public abstract @NotNull PackagingElement<?> createTestModuleOutput(@NotNull Module module);

  public abstract @NotNull List<PackagingElement<?>> createLibraryElements(@NotNull Library library);

  public abstract @NotNull List<? extends PackagingElement<?>> createLibraryElements(@NotNull LibraryEntity libraryEntity, String moduleName);

  public abstract @NotNull PackagingElement<?> createArtifactElement(@NotNull ArtifactPointer artifactPointer, @NotNull Project project);

  public abstract @NotNull PackagingElement<?> createArtifactElement(@NotNull Artifact artifact, @NotNull Project project);

  public abstract @NotNull PackagingElement<?> createArtifactElement(@NotNull String artifactName, @NotNull Project project);

  public abstract @NotNull PackagingElement<?> createLibraryFiles(@NotNull String libraryName, @NotNull @NonNls String level, @NonNls String moduleName);


  public abstract @NotNull PackagingElement<?> createDirectoryCopyWithParentDirectories(@NotNull @NonNls String filePath, @NotNull @NonNls String relativeOutputPath);

  public abstract @NotNull PackagingElement<?> createExtractedDirectoryWithParentDirectories(@NotNull @NonNls String jarPath, @NotNull @NonNls String pathInJar,
                                                                                             @NotNull @NonNls String relativeOutputPath);

  public abstract @NotNull PackagingElement<?> createExtractedDirectory(@NotNull VirtualFile jarEntry);

  public abstract @NotNull PackagingElement<?> createFileCopyWithParentDirectories(@NotNull @NonNls String filePath, @NotNull @NonNls String relativeOutputPath,
                                                                                   @Nullable @NonNls String outputFileName);

  public abstract @NotNull PackagingElement<?> createFileCopyWithParentDirectories(@NotNull @NonNls String filePath, @NotNull @NonNls String relativeOutputPath);


  public abstract @NotNull CompositePackagingElement<?> getOrCreateDirectory(@NotNull CompositePackagingElement<?> parent, @NotNull @NonNls String relativePath);

  public abstract @NotNull CompositePackagingElement<?> getOrCreateArchive(@NotNull CompositePackagingElement<?> parent, @NotNull @NonNls String relativePath);

  public abstract void addFileCopy(@NotNull CompositePackagingElement<?> root, @NotNull @NonNls String outputDirectoryPath,
                                   @NotNull @NonNls String sourceFilePath, @NonNls String outputFileName, boolean addAsFirstChild);

  public abstract void addFileCopy(@NotNull CompositePackagingElement<?> root, @NotNull @NonNls String outputDirectoryPath, @NotNull @NonNls String sourceFilePath);

  public abstract @NotNull PackagingElement<?> createParentDirectories(@NotNull @NonNls String relativeOutputPath, @NotNull PackagingElement<?> element);


  public abstract @NotNull List<? extends PackagingElement<?>> createParentDirectories(@NotNull @NonNls String relativeOutputPath, @NotNull List<? extends PackagingElement<?>> elements);

  public abstract CompositePackagingElementType<?> @NotNull [] getCompositeElementTypes();

  public abstract @Nullable PackagingElementType<?> findElementType(@NonNls String id);

  public abstract PackagingElementType<?> @NotNull [] getNonCompositeElementTypes();

  public abstract @NotNull List<PackagingElementType> getAllElementTypes();

  public abstract ComplexPackagingElementType<?> @NotNull [] getComplexElementTypes();
}
