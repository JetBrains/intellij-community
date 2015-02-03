package org.jetbrains.builtInWebServer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PrefixlessWebServerRootsProvider extends WebServerRootsProvider {
  @Nullable
  @Override
  public final PathInfo resolve(@NotNull String path, @NotNull Project project) {
    return resolve(path, project, WebServerPathToFileManager.getInstance(project).getResolver(path));
  }

  @Nullable
  public abstract PathInfo resolve(@NotNull String path, @NotNull Project project, @NotNull PairFunction<String, VirtualFile, VirtualFile> resolver);
}