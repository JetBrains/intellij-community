// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.impl.elements.FileOrDirectoryCopyPackagingElement;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ArtifactVirtualFileListener implements BulkFileListener {
  private final Project project;
  private final CachedValue<Map<String, List<Artifact>>> parentPathsToArtifacts;

  ArtifactVirtualFileListener(@NotNull Project project) {
    this.project = project;
    parentPathsToArtifacts = CachedValuesManager.getManager(project).createCachedValue(() -> {
      ArtifactManager artifactManager = ArtifactManager.getInstance(project);
      Map<String, List<Artifact>> result = computeParentPathToArtifactMap(artifactManager);
      return CachedValueProvider.Result.createSingleDependency(result, artifactManager.getModificationTracker());
    }, false);
  }

  private static Map<String, List<Artifact>> computeParentPathToArtifactMap(ArtifactManager artifactManager) {
    Map<String, List<Artifact>> result = new HashMap<>();
    for (Artifact artifact : artifactManager.getArtifacts()) {
      ArtifactUtil.processFileOrDirectoryCopyElements(artifact, new PackagingElementProcessor<>() {
        @Override
        public boolean process(@NotNull FileOrDirectoryCopyPackagingElement<?> element, @NotNull PackagingElementPath pathToElement) {
          String path = element.getFilePath();
          while (path.length() > 0) {
            result.computeIfAbsent(path, __ -> new ArrayList<>()).add(artifact);
            path = PathUtil.getParentPath(path);
          }
          return true;
        }
      }, artifactManager.getResolvingContext(), false);
    }
    return result;
  }

  @Override
  public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
    for (VFileEvent event : events) {
      if (event instanceof VFileMoveEvent) {
        filePathChanged(((VFileMoveEvent)event).getOldPath(), event.getPath());
      }
      else if (event instanceof VFilePropertyChangeEvent) {
        propertyChanged((VFilePropertyChangeEvent)event);
      }
    }
  }

  private void filePathChanged(@NotNull String oldPath, @NotNull String newPath) {
    List<Artifact> artifacts = parentPathsToArtifacts.getValue().get(oldPath);
    if (artifacts == null) {
      return;
    }

    ArtifactManager artifactManager = ArtifactManager.getInstance(project);
    ModifiableArtifactModel model = artifactManager.createModifiableModel();
    for (Artifact artifact : artifacts) {
      Artifact copy = model.getOrCreateModifiableArtifact(artifact);
      ArtifactUtil.processFileOrDirectoryCopyElements(copy, new PackagingElementProcessor<>() {
        @Override
        public boolean process(@NotNull FileOrDirectoryCopyPackagingElement<?> element, @NotNull PackagingElementPath pathToElement) {
          final String path = element.getFilePath();
          if (FileUtil.startsWith(path, oldPath)) {
            element.setFilePath(newPath + path.substring(oldPath.length()));
          }
          return true;
        }
      }, artifactManager.getResolvingContext(), false);
    }
    model.commit();
  }

  private void propertyChanged(@NotNull VFilePropertyChangeEvent event) {
    if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
      VirtualFile parent = event.getFile().getParent();
      if (parent != null) {
        String parentPath = parent.getPath();
        filePathChanged(parentPath + "/" + event.getOldValue(), parentPath + "/" + event.getNewValue());
      }
    }
  }
}
