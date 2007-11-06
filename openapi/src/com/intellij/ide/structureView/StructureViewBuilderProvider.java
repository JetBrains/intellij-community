/*
 * @author max
 */
package com.intellij.ide.structureView;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface StructureViewBuilderProvider {
  /**
   * Returns the structure view builder for the specified file.
   * @param fileType file type of the file to provide structure for
   * @param file The file for which the structure view builder is requested.
   * @param project The project to which the file belongs.
   * @return The structure view builder, or null if no structure view is available for the file.
   */

  @Nullable
  StructureViewBuilder getStructureViewBuilder(@NotNull FileType fileType, @NotNull VirtualFile file, @NotNull Project project);  
}