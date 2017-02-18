/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.packaging.impl.artifacts;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.impl.elements.FileOrDirectoryCopyPackagingElement;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
 */
public class ArtifactVirtualFileListener extends VirtualFileAdapter {
  private final CachedValue<MultiValuesMap<String, Artifact>> myParentPathsToArtifacts;
  private final ArtifactManagerImpl myArtifactManager;

  public ArtifactVirtualFileListener(Project project, final ArtifactManagerImpl artifactManager) {
    myArtifactManager = artifactManager;
    myParentPathsToArtifacts =
      CachedValuesManager.getManager(project).createCachedValue(() -> {
        MultiValuesMap<String, Artifact> result = computeParentPathToArtifactMap();
        return CachedValueProvider.Result.createSingleDependency(result, artifactManager.getModificationTracker());
      }, false);
  }

  private MultiValuesMap<String, Artifact> computeParentPathToArtifactMap() {
    final MultiValuesMap<String, Artifact> result = new MultiValuesMap<>();
    for (final Artifact artifact : myArtifactManager.getArtifacts()) {
      ArtifactUtil.processFileOrDirectoryCopyElements(artifact, new PackagingElementProcessor<FileOrDirectoryCopyPackagingElement<?>>() {
        @Override
        public boolean process(@NotNull FileOrDirectoryCopyPackagingElement<?> element, @NotNull PackagingElementPath pathToElement) {
          String path = element.getFilePath();
          while (path.length() > 0) {
            result.put(path, artifact);
            path = PathUtil.getParentPath(path);
          }
          return true;
        }
      }, myArtifactManager.getResolvingContext(), false);
    }
    return result;
  }


  @Override
  public void fileMoved(@NotNull VirtualFileMoveEvent event) {
    final String oldPath = event.getOldParent().getPath() + "/" + event.getFileName();
    filePathChanged(oldPath, event.getNewParent().getPath() + "/" + event.getFileName());
  }

  private void filePathChanged(@NotNull final String oldPath, @NotNull final String newPath) {
    final Collection<Artifact> artifacts = myParentPathsToArtifacts.getValue().get(oldPath);
    if (artifacts != null) {
      final ModifiableArtifactModel model = myArtifactManager.createModifiableModel();
      for (Artifact artifact : artifacts) {
        final Artifact copy = model.getOrCreateModifiableArtifact(artifact);
        ArtifactUtil.processFileOrDirectoryCopyElements(copy, new PackagingElementProcessor<FileOrDirectoryCopyPackagingElement<?>>() {
          @Override
          public boolean process(@NotNull FileOrDirectoryCopyPackagingElement<?> element, @NotNull PackagingElementPath pathToElement) {
            final String path = element.getFilePath();
            if (FileUtil.startsWith(path, oldPath)) {
              element.setFilePath(newPath + path.substring(oldPath.length()));
            }
            return true;
          }
        }, myArtifactManager.getResolvingContext(), false);
      }
      model.commit();
    }
  }

  @Override
  public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
    if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
      final VirtualFile parent = event.getParent();
      if (parent != null) {
        filePathChanged(parent.getPath() + "/" + event.getOldValue(), parent.getPath() + "/" + event.getNewValue());
      }
    }
  }
}
