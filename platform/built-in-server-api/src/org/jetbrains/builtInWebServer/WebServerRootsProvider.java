package org.jetbrains.builtInWebServer;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class WebServerRootsProvider {
  public static final ExtensionPointName<WebServerRootsProvider> EP_NAME = ExtensionPointName.create("org.jetbrains.webServerRootsProvider");

  @Nullable
  public abstract PathInfo resolve(@NotNull String path, @NotNull Project project);

  @Nullable
  public abstract PathInfo getPathInfo(@NotNull VirtualFile file, @NotNull Project project);

  public boolean isClearCacheOnFileContentChanged(@NotNull VirtualFile file) {
    return false;
  }
}