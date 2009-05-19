package com.intellij.packaging.elements;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public abstract class PackagingElementFactory {

  public static PackagingElementFactory getInstance() {
    return ServiceManager.getService(PackagingElementFactory.class);
  }

  public abstract PackagingElementType<?>[] getNonCompositeElementTypes();

  @NotNull
  public abstract ArtifactRootElement<?> createArtifactRootElement();

  @NotNull
  public abstract CompositePackagingElement<?> getOrCreateDirectory(@NotNull CompositePackagingElement<?> parent, @NotNull String relativePath);

  @NotNull
  public abstract CompositePackagingElement<?> getOrCreateArchive(@NotNull CompositePackagingElement<?> parent, @NotNull String relativePath);

  public abstract CompositePackagingElement<?> createDirectory(@NotNull @NonNls String directoryName);

  public abstract CompositePackagingElement<?> createArchive(@NotNull @NonNls String archiveFileName);

  public abstract PackagingElement<?> createFileCopy(@NotNull String filePath, @NotNull String relativeOutputPath);

  public abstract PackagingElement<?> createModuleOutput(@NotNull String moduleName);

  public abstract List<? extends PackagingElement<?>> createLibraryElements(@NotNull Library library);

  public abstract PackagingElement<?> createLibraryFiles(@NotNull String level, @NotNull String name);

  public abstract CompositePackagingElementType<?>[] getCompositeElementTypes();

  public abstract PackagingElementType<?> findElementType(String id);

  public abstract PackagingElementType[] getAllElementTypes();

  public abstract PackagingElement<?> createArtifactElement(@NotNull Artifact artifact);

  public abstract PackagingElement<?> createParentDirectories(@NotNull String relativeOutputPath, @NotNull PackagingElement<?> element);
}
