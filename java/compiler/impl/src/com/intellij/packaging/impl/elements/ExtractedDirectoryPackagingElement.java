// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.elements;

import com.intellij.java.workspace.entities.ExtractedDirectoryPackagingElementEntity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingExternalMapping;
import com.intellij.packaging.impl.ui.ExtractedDirectoryPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.platform.backend.workspace.VirtualFileUrls;
import com.intellij.platform.workspace.storage.EntitySource;
import com.intellij.platform.workspace.storage.MutableEntityStorage;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager;
import com.intellij.util.xmlb.annotations.Attribute;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    return "extracted:" + getMyFilePath() + "!" + getMyPathInJar();
  }

  @Override
  public VirtualFile findFile() {
    final VirtualFile jarFile = super.findFile();
    if (jarFile == null) return null;

    final VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(jarFile);
    if ("/".equals(getMyPathInJar())) return jarRoot;
    return jarRoot != null ? jarRoot.findFileByRelativePath(getMyPathInJar()) : null;
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof ExtractedDirectoryPackagingElement && super.isEqualTo(element)
           && Objects.equals(getMyPathInJar(), ((ExtractedDirectoryPackagingElement)element).getPathInJar());
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
    return getMyPathInJar();
  }

  public void setPathInJar(String pathInJar) {
    String myPathInJarBefore = getMyPathInJar();
    this.update(
      () -> myPathInJar = pathInJar,
      (builder, entity) -> {
        if (myPathInJarBefore.equals(pathInJar)) return;

        builder.modifyEntity(ExtractedDirectoryPackagingElementEntity.Builder.class, entity, ent -> {
          ent.setPathInArchive(pathInJar);
          return Unit.INSTANCE;
        });
    });
  }

  @Override
  public WorkspaceEntity getOrAddEntity(@NotNull MutableEntityStorage diff,
                                        @NotNull EntitySource source,
                                        @NotNull Project project) {
    WorkspaceEntity existingEntity = getExistingEntity(diff);
    if (existingEntity != null) return existingEntity;

    VirtualFileUrlManager fileUrlManager = VirtualFileUrls.getVirtualFileUrlManager(project);
    Objects.requireNonNull(this.myFilePath, "filePath is not specified");
    Objects.requireNonNull(this.myPathInJar, "pathInJar is not specified");
    VirtualFileUrl fileUrl = fileUrlManager.getOrCreateFromUri(VfsUtilCore.pathToUrl(this.myFilePath));

    ExtractedDirectoryPackagingElementEntity addedEntity = diff.addEntity(ExtractedDirectoryPackagingElementEntity.create(fileUrl, this.myPathInJar, source));
    diff.getMutableExternalMapping(PackagingExternalMapping.key).addMapping(addedEntity, this);
    return addedEntity;
  }

  @Nullable
  private String getMyPathInJar() {
    if (myStorage == null) {
      return myPathInJar;
    } else {
      ExtractedDirectoryPackagingElementEntity entity = (ExtractedDirectoryPackagingElementEntity)getThisEntity();
      String path = entity.getPathInArchive();
      if (!Objects.equals(myPathInJar, path)) {
        myPathInJar = path;
      }
      return path;
    }
  }
}
