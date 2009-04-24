package com.intellij.packaging.elements;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class PackagingElementFactory {

  public static PackagingElementFactory getInstance() {
    return ServiceManager.getService(PackagingElementFactory.class);
  }

  public abstract PackagingElementType<?>[] getNonCompositeElementTypes();

  public abstract PackagingElement<?> createDirectory(@NotNull @NonNls String directoryName);

  public abstract PackagingElement<?> createArchive(@NotNull @NonNls String archiveFileName);

  public abstract PackagingElement<?> createFileCopy(@NotNull String filePath, @NotNull String relativeOutputPath);

  public abstract CompositePackagingElementType<?>[] getCompositeElementTypes();

  public abstract PackagingElementType<?> findElementType(String id);

  public abstract PackagingElementType[] getAllElementTypes();
}
