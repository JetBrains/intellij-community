package com.intellij.packaging.elements;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
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

  @NotNull
  public abstract PackagingElement<?> createModuleOutput(@NotNull String moduleName);

  @NotNull
  public abstract List<? extends PackagingElement<?>> createLibraryElements(@NotNull Library library);

  @NotNull
  public abstract PackagingElement<?> createArtifactElement(@NotNull Artifact artifact);

  @NotNull
  public abstract PackagingElement<?> createLibraryFiles(@NotNull String level, @NotNull String name);

  @NotNull
  public abstract PackagingElement<?> createFileCopy(@NotNull String filePath);


  @NotNull
  public abstract PackagingElement<?> createFileCopyWithParentDirectories(@NotNull String filePath, @NotNull String relativeOutputPath);

  @NotNull
  public abstract CompositePackagingElement<?> getOrCreateDirectory(@NotNull CompositePackagingElement<?> parent, @NotNull String relativePath);

  @NotNull
  public abstract CompositePackagingElement<?> getOrCreateArchive(@NotNull CompositePackagingElement<?> parent, @NotNull String relativePath);

  public abstract void addFileCopy(@NotNull CompositePackagingElement<?> root, @NotNull String outputDirectoryPath, @NotNull String sourceFilePath);

  @NotNull
  public abstract PackagingElement<?> createParentDirectories(@NotNull String relativeOutputPath, @NotNull PackagingElement<?> element);


  @NotNull
  public abstract CompositePackagingElementType<?>[] getCompositeElementTypes();

  @Nullable
  public abstract PackagingElementType<?> findElementType(String id);

  @NotNull
  public abstract PackagingElementType<?>[] getNonCompositeElementTypes();

  @NotNull
  public abstract PackagingElementType[] getAllElementTypes();

}
