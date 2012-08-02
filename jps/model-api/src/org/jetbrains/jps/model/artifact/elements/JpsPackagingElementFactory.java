package org.jetbrains.jps.model.artifact.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.artifact.JpsArtifactReference;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.service.JpsServiceManager;

/**
 * @author nik
 */
public abstract class JpsPackagingElementFactory {
  public abstract JpsCompositePackagingElement getOrCreateDirectory(@NotNull JpsCompositePackagingElement root, @NotNull String path);

  public abstract JpsCompositePackagingElement getOrCreateArchive(@NotNull JpsCompositePackagingElement root, @NotNull String path);

  public static JpsPackagingElementFactory getInstance() {
    return JpsServiceManager.getInstance().getService(JpsPackagingElementFactory.class);
  }

  @NotNull
  public abstract JpsDirectoryCopyPackagingElement createDirectoryCopy(@NotNull String directoryPath);

  public abstract JpsPackagingElement createParentDirectories(String path, JpsPackagingElement element);

  @NotNull
  public abstract JpsFileCopyPackagingElement createFileCopy(@NotNull String filePath, @Nullable String outputFileName);

  @NotNull
  public abstract JpsExtractedDirectoryPackagingElement createExtractedDirectory(@NotNull String jarPath, @NotNull String pathInJar);

  @NotNull
  public abstract JpsDirectoryPackagingElement createDirectory(@NotNull String directoryName);

  @NotNull
  public abstract JpsArchivePackagingElement createArchive(@NotNull String archiveName);

  @NotNull
  public abstract JpsArtifactRootElement createArtifactRoot();

  @NotNull
  public abstract JpsLibraryFilesPackagingElement createLibraryElement(@NotNull JpsLibraryReference reference);

  @NotNull
  public abstract JpsArtifactOutputPackagingElement createArtifactOutput(@NotNull JpsArtifactReference reference);

}
