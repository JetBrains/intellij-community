// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactBridge;
import com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactManagerBridge;
import com.intellij.packaging.impl.elements.FileOrDirectoryCopyPackagingElement;
import com.intellij.util.PathUtil;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.storage.CachedValue;
import com.intellij.workspaceModel.storage.ExternalEntityMapping;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.ArtifactEntity;
import org.jetbrains.annotations.NotNull;

import java.util.*;

final class ArtifactVirtualFileListener implements BulkFileListener {
  private static final Logger LOG = Logger.getInstance(ArtifactVirtualFileListener.class);
  private final Project project;
  private final CachedValue<Map<String, List<ArtifactEntity>>> parentPathsToArtifacts;

  ArtifactVirtualFileListener(@NotNull Project project) {
    this.project = project;
    parentPathsToArtifacts = new CachedValue<>(ArtifactVirtualFileListener::computeParentPathToArtifactMap);
  }

  private static Map<String, List<ArtifactEntity>> computeParentPathToArtifactMap(WorkspaceEntityStorage storage) {
    Map<String, List<ArtifactEntity>> result = new HashMap<>();
    Iterator<ArtifactEntity> entities = storage.entities(ArtifactEntity.class).iterator();
    while (entities.hasNext()) {
      ArtifactEntity artifact = entities.next();
      PackagingElementProcessing.processFileOrDirectoryCopyElements(artifact, entity -> {
        String path = VfsUtilCore.urlToPath(entity.getFilePath().getUrl());
        while (path.length() > 0) {
          result.computeIfAbsent(path, __ -> new ArrayList<>()).add(artifact);
          path = PathUtil.getParentPath(path);
        }
        return true;
      });
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
    List<ArtifactEntity> artifactEntities = getParentPathToArtifacts().get(oldPath);
    if (artifactEntities == null) {
      return;
    }

    ArtifactManager artifactManager = ArtifactManager.getInstance(project);
    //this is needed to set up mapping from ArtifactEntity to ArtifactBridge
    artifactManager.getArtifacts();
    
    WorkspaceEntityStorage storage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
    ExternalEntityMapping<ArtifactBridge> artifactsMap = ArtifactManagerBridge.Companion.getArtifactsMap(storage);
    ModifiableArtifactModel model = artifactManager.createModifiableModel();
    for (ArtifactEntity artifactEntity : artifactEntities) {
      ArtifactBridge artifact = artifactsMap.getDataByEntity(artifactEntity);
      if (artifact == null) continue;
      
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

  private Map<String, List<ArtifactEntity>> getParentPathToArtifacts() {
    return WorkspaceModel.getInstance(project).getEntityStorage().cachedValue(parentPathsToArtifacts);
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
