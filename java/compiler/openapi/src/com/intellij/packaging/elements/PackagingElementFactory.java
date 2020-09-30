/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.packaging.elements;

import com.intellij.openapi.components.ServiceManager;
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
    return ServiceManager.getService(PackagingElementFactory.class);
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

  public abstract void addFileCopy(@NotNull CompositePackagingElement<?> root, @NotNull @NonNls String outputDirectoryPath, @NotNull @NonNls String sourceFilePath,
                                   final @NonNls String outputFileName);

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
