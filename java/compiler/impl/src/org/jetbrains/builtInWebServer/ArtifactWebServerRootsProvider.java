package org.jetbrains.builtInWebServer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ArtifactWebServerRootsProvider extends WebServerRootsProvider.PrefixlessWebServerRootsProvider {
  @Nullable
  @Override
  public Pair<VirtualFile, Pair<VirtualFile, String>> resolve(@NotNull String path, @NotNull Project project, @NotNull PairFunction<String, VirtualFile, VirtualFile> resolver) {
    for (Artifact artifact : ArtifactManager.getInstance(project).getArtifacts()) {
      VirtualFile root = artifact.getOutputFile();
      if (root != null) {
        VirtualFile file = root.findFileByRelativePath(path);
        if (file != null) {
          return Pair.create(file, new Pair<VirtualFile, String>(root, null));
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Pair<VirtualFile, String> getRoot(@NotNull VirtualFile file, @NotNull Project project) {
    for (Artifact artifact : ArtifactManager.getInstance(project).getArtifacts()) {
      VirtualFile root = artifact.getOutputFile();
      if (root != null && VfsUtilCore.isAncestor(root, file, true)) {
        return Pair.create(root, null);
      }
    }
    return null;
  }
}