// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.elements;

import com.intellij.openapi.project.Project;
import com.intellij.packaging.impl.ui.DirectoryCopyPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.workspaceModel.ide.VirtualFileUrlManagerUtil;
import com.intellij.workspaceModel.storage.EntitySource;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.MutableEntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.ExtensionsKt;
import com.intellij.workspaceModel.storage.bridgeEntities.DirectoryCopyPackagingElementEntity;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager;
import org.jetbrains.annotations.NotNull;

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

    VirtualFileUrlManager fileUrlManager = VirtualFileUrlManagerUtil.getInstance(VirtualFileUrlManager.Companion, project);
    VirtualFileUrl fileUrl = fileUrlManager.fromPath(myFilePath);
    DirectoryCopyPackagingElementEntity addedEntity =
      ExtensionsKt.addDirectoryCopyPackagingElementEntity(diff, fileUrl, source);
    diff.getMutableExternalMapping("intellij.artifacts.packaging.elements").addMapping(addedEntity, this);
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
