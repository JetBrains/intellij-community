package com.intellij.psi.file;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public abstract class FileLookupInfoProvider {
  public static ExtensionPointName<FileLookupInfoProvider> EP_NAME = ExtensionPointName.create("com.intellij.fileLookupInfoProvider");

  @NotNull
  public abstract FileType[] getFileTypes();

  @Nullable
  public abstract Pair<String, String> getLookupInfo(@NotNull final VirtualFile file, Project project);
}
