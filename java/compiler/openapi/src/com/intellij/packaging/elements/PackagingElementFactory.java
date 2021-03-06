// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.elements;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactPointer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class PackagingElementFactory {

  public static PackagingElementFactory getInstance() {
    return ApplicationManager.getApplication().getService(PackagingElementFactory.class);
  }

  @NotNull
  public abstract ArtifactRootElement<?> createArtifactRootElement();

  @NotNull
  public abstract CompositePackagingElement<?> createDirectory(@NotNull @NonNls String directoryName);

  @NotNull
  public abstract CompositePackagingElement<?> createArchive(@NotNull @NonNls String archiveFileName);

  public abstract PackagingElement<?> createFileCopy(@NotNull @NonNls String filePath, @Nullable @NonNls String outputFileName);

  @NotNull
  public abstract PackagingElement<?> createModuleOutput(@NotNull @NonNls String moduleName, @NotNull Project project);

  @NotNull
  public abstract PackagingElement<?> createModuleOutput(@NotNull Module module);

  @NotNull
  public abstract PackagingElement<?> createModuleSource(@NotNull Module module);

  @NotNull
  public abstract PackagingElement<?> createTestModuleOutput(@NotNull Module module);

  @NotNull
  public abstract List<? extends PackagingElement<?>> createLibraryElements(@NotNull Library library);

  @NotNull
  public abstract PackagingElement<?> createArtifactElement(@NotNull ArtifactPointer artifactPointer, @NotNull Project project);

  @NotNull
  public abstract PackagingElement<?> createArtifactElement(@NotNull Artifact artifact, @NotNull Project project);

  @NotNull
  public abstract PackagingElement<?> createLibraryFiles(@NotNull String libraryName, @NotNull @NonNls String level, @NonNls String moduleName);


  @NotNull
  public abstract PackagingElement<?> createDirectoryCopyWithParentDirectories(@NotNull @NonNls String filePath, @NotNull @NonNls String relativeOutputPath);

  @NotNull
  public abstract PackagingElement<?> createExtractedDirectoryWithParentDirectories(@NotNull @NonNls String jarPath, @NotNull @NonNls String pathInJar,
                                                                                    @NotNull @NonNls String relativeOutputPath);

  @NotNull
  public abstract PackagingElement<?> createExtractedDirectory(@NotNull VirtualFile jarEntry);

  @NotNull
  public abstract PackagingElement<?> createFileCopyWithParentDirectories(@NotNull @NonNls String filePath, @NotNull @NonNls String relativeOutputPath,
                                                                          @Nullable @NonNls String outputFileName);

  @NotNull
  public abstract PackagingElement<?> createFileCopyWithParentDirectories(@NotNull @NonNls String filePath, @NotNull @NonNls String relativeOutputPath);


  @NotNull
  public abstract CompositePackagingElement<?> getOrCreateDirectory(@NotNull CompositePackagingElement<?> parent, @NotNull @NonNls String relativePath);

  @NotNull
  public abstract CompositePackagingElement<?> getOrCreateArchive(@NotNull CompositePackagingElement<?> parent, @NotNull @NonNls String relativePath);

  public abstract void addFileCopy(@NotNull CompositePackagingElement<?> root, @NotNull @NonNls String outputDirectoryPath,
                                   @NotNull @NonNls String sourceFilePath, @NonNls String outputFileName, boolean addAsFirstChild);

  public abstract void addFileCopy(@NotNull CompositePackagingElement<?> root, @NotNull @NonNls String outputDirectoryPath, @NotNull @NonNls String sourceFilePath);

  @NotNull
  public abstract PackagingElement<?> createParentDirectories(@NotNull @NonNls String relativeOutputPath, @NotNull PackagingElement<?> element);


  @NotNull
  public abstract List<? extends PackagingElement<?>> createParentDirectories(@NotNull @NonNls String relativeOutputPath, @NotNull List<? extends PackagingElement<?>> elements);

  public abstract CompositePackagingElementType<?> @NotNull [] getCompositeElementTypes();

  @Nullable
  public abstract PackagingElementType<?> findElementType(@NonNls String id);

  public abstract PackagingElementType<?> @NotNull [] getNonCompositeElementTypes();

  public abstract PackagingElementType @NotNull [] getAllElementTypes();

  public abstract ComplexPackagingElementType<?> @NotNull [] getComplexElementTypes();
}
