// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.elements;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.ui.ExtractedDirectoryPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.workspaceModel.ide.VirtualFileUrlManagerUtil;
import com.intellij.workspaceModel.storage.EntitySource;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder;
import com.intellij.workspaceModel.storage.bridgeEntities.BridgeModelModifiableEntitiesKt;
import com.intellij.workspaceModel.storage.bridgeEntities.ExtractedDirectoryPackagingElementEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ModifiableExtractedDirectoryPackagingElementEntity;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ExtractedDirectoryPackagingElement extends FileOrDirectoryCopyPackagingElement<ExtractedDirectoryPackagingElement> {
  private String myPathInJar;

  public ExtractedDirectoryPackagingElement() {
    super(PackagingElementFactoryImpl.EXTRACTED_DIRECTORY_ELEMENT_TYPE);
  }

  public ExtractedDirectoryPackagingElement(String jarPath, String pathInJar) {
    super(PackagingElementFactoryImpl.EXTRACTED_DIRECTORY_ELEMENT_TYPE, jarPath);
    myPathInJar = pathInJar;
    if (!StringUtil.startsWithChar(myPathInJar, '/')) {
      myPathInJar = "/" + myPathInJar;
    }
    if (!StringUtil.endsWithChar(myPathInJar, '/')) {
      myPathInJar += "/";
    }
  }

  @NotNull
  @Override
  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new ExtractedDirectoryPresentation(this);
  }

  @Override
  public String toString() {
    return "extracted:" + myFilePath + "!" + myPathInJar;
  }

  @Override
  public VirtualFile findFile() {
    final VirtualFile jarFile = super.findFile();
    if (jarFile == null) return null;

    final VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(jarFile);
    if ("/".equals(myPathInJar)) return jarRoot;
    return jarRoot != null ? jarRoot.findFileByRelativePath(myPathInJar) : null;
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof ExtractedDirectoryPackagingElement && super.isEqualTo(element)
           && Objects.equals(myPathInJar, ((ExtractedDirectoryPackagingElement)element).getPathInJar());
  }

  @Override
  public ExtractedDirectoryPackagingElement getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull ExtractedDirectoryPackagingElement state) {
    myFilePath = state.getFilePath();
    myPathInJar = state.getPathInJar();
  }

  @Attribute("path-in-jar")
  public String getPathInJar() {
    return myPathInJar;
  }

  public void setPathInJar(String pathInJar) {
    String myPathInJarBefore = myPathInJar;
    this.update(
      () -> myPathInJar = pathInJar,
      (builder, entity) -> {
        if (myPathInJarBefore.equals(pathInJar)) return;

        builder.modifyEntity(ModifiableExtractedDirectoryPackagingElementEntity.class, entity, ent -> {
          ent.setPathInArchive(pathInJar);
          return Unit.INSTANCE;
        });
    });
  }

  @Override
  public WorkspaceEntity getOrAddEntity(@NotNull WorkspaceEntityStorageBuilder diff,
                                        @NotNull EntitySource source,
                                        @NotNull Project project) {
    WorkspaceEntity existingEntity = getExistingEntity(diff);
    if (existingEntity != null) return existingEntity;

    VirtualFileUrlManager fileUrlManager = VirtualFileUrlManagerUtil.getInstance(VirtualFileUrlManager.Companion, project);
    VirtualFileUrl fileUrl = fileUrlManager.fromPath(this.myFilePath);

    ExtractedDirectoryPackagingElementEntity addedEntity =
      BridgeModelModifiableEntitiesKt.addExtractedDirectoryPackagingElementEntity(diff, fileUrl, this.myPathInJar, source);
    diff.getMutableExternalMapping("intellij.artifacts.packaging.elements").addMapping(addedEntity, this);
    return addedEntity;
  }
}
