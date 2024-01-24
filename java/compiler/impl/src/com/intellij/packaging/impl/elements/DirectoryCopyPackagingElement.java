// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.elements;

import com.intellij.java.workspace.entities.DirectoryCopyPackagingElementEntity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.packaging.elements.PackagingExternalMapping;
import com.intellij.packaging.impl.ui.DirectoryCopyPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.platform.backend.workspace.VirtualFileUrls;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.storage.EntitySource;
import com.intellij.platform.workspace.storage.MutableEntityStorage;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class DirectoryCopyPackagingElement extends FileOrDirectoryCopyPackagingElement<DirectoryCopyPackagingElement> {
  public DirectoryCopyPackagingElement() {
    super(PackagingElementFactoryImpl.DIRECTORY_COPY_ELEMENT_TYPE);
  }

  public DirectoryCopyPackagingElement(String directoryPath) {
    this();
    myFilePath = directoryPath;
  }

  @NotNull
  @Override
  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new DirectoryCopyPresentation(getMyFilePath());
  }

  @Override
  public WorkspaceEntity getOrAddEntity(@NotNull MutableEntityStorage diff,
                                        @NotNull EntitySource source,
                                        @NotNull Project project) {
    WorkspaceEntity existingEntity = getExistingEntity(diff);
    if (existingEntity != null) return existingEntity;

    VirtualFileUrlManager fileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager();
    Objects.requireNonNull(myFilePath, "filePath is not specified");
    VirtualFileUrl fileUrl = fileUrlManager.getOrCreateFromUri(VfsUtilCore.pathToUrl(myFilePath));
    DirectoryCopyPackagingElementEntity addedEntity = diff.addEntity(DirectoryCopyPackagingElementEntity.create(fileUrl, source));
    diff.getMutableExternalMapping(PackagingExternalMapping.key).addMapping(addedEntity, this);
    return addedEntity;
  }

  @Override
  public DirectoryCopyPackagingElement getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull DirectoryCopyPackagingElement state) {
    myFilePath = state.getFilePath();
  }

  @Override
  public String toString() {
    return "dir:" + getMyFilePath();
  }
}
