// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.elements;

import com.intellij.java.workspace.entities.FileCopyPackagingElementEntity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingExternalMapping;
import com.intellij.packaging.elements.RenameablePackagingElement;
import com.intellij.packaging.impl.ui.FileCopyPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.platform.backend.workspace.VirtualFileUrls;
import com.intellij.platform.workspace.storage.EntitySource;
import com.intellij.platform.workspace.storage.MutableEntityStorage;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager;
import com.intellij.util.PathUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import kotlin.Unit;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;

public class FileCopyPackagingElement extends FileOrDirectoryCopyPackagingElement<FileCopyPackagingElement> implements RenameablePackagingElement {
  @NonNls public static final String OUTPUT_FILE_NAME_ATTRIBUTE = "output-file-name";
  private String myRenamedOutputFileName;

  public FileCopyPackagingElement() {
    super(PackagingElementFactoryImpl.FILE_COPY_ELEMENT_TYPE);
  }

  public FileCopyPackagingElement(String filePath) {
    this();
    myFilePath = filePath;
  }

  public FileCopyPackagingElement(String filePath, String outputFileName) {
    this(filePath);
    myRenamedOutputFileName = outputFileName;
  }

  @Override
  @NotNull
  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new FileCopyPresentation(getMyFilePath(), getOutputFileName());
  }

  public String getOutputFileName() {
    return getMyRenamedOutputFileName() != null ? getMyRenamedOutputFileName() : PathUtil.getFileName(getMyFilePath());
  }

  @NonNls @Override
  public String toString() {
    return "file:" + getMyFilePath() + (getMyRenamedOutputFileName() != null ? ",rename to:" + getMyRenamedOutputFileName() : "");
  }

  public boolean isDirectory() {
    return new File(FileUtil.toSystemDependentName(getMyFilePath())).isDirectory();
  }


  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof FileCopyPackagingElement && super.isEqualTo(element)
           && Objects.equals(getMyRenamedOutputFileName(), ((FileCopyPackagingElement)element).getRenamedOutputFileName());
  }

  @Override
  public FileCopyPackagingElement getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull FileCopyPackagingElement state) {
    setFilePath(state.getFilePath());
    setRenamedOutputFileName(state.getRenamedOutputFileName());
  }

  @Nullable
  @Attribute(OUTPUT_FILE_NAME_ATTRIBUTE)
  public String getRenamedOutputFileName() {
    return getMyRenamedOutputFileName();
  }

  public void setRenamedOutputFileName(String renamedOutputFileName) {
    String renamedBefore = getMyRenamedOutputFileName();
    this.update(
      () -> myRenamedOutputFileName = renamedOutputFileName,
      (builder, entity) -> {
        if (Objects.equals(renamedBefore, renamedOutputFileName)) return;

        builder.modifyEntity(FileCopyPackagingElementEntity.Builder.class, entity, ent -> {
          ent.setRenamedOutputFileName(renamedOutputFileName);
          return Unit.INSTANCE;
        });
      }
    );
  }

  @Override
  public String getName() {
    return getOutputFileName();
  }

  @Override
  public boolean canBeRenamed() {
    return !isDirectory();
  }

  @Override
  public void rename(@NotNull String newName) {
    String updatedName = newName.equals(PathUtil.getFileName(getMyFilePath())) ? null : newName;
    this.update(
      () -> myRenamedOutputFileName = updatedName,
      (builder, entity) -> {
        builder.modifyEntity(FileCopyPackagingElementEntity.Builder.class, entity, ent -> {
          ent.setRenamedOutputFileName(updatedName);
          return Unit.INSTANCE;
        });
      }
    );
  }

  @Nullable
  public VirtualFile getLibraryRoot() {
    final String url = VfsUtil.getUrlForLibraryRoot(new File(FileUtil.toSystemDependentName(getFilePath())));
    return VirtualFileManager.getInstance().findFileByUrl(url);
  }

  @Override
  public WorkspaceEntity getOrAddEntity(@NotNull MutableEntityStorage diff,
                                        @NotNull EntitySource source,
                                        @NotNull Project project) {
    WorkspaceEntity existingEntity = getExistingEntity(diff);
    if (existingEntity != null) return existingEntity;

    String renamedOutputFileName = this.myRenamedOutputFileName;
    String filePath = this.myFilePath;
    Objects.requireNonNull(filePath, "filePath is not specified");
    FileCopyPackagingElementEntity addedEntity;
    VirtualFileUrlManager fileUrlManager = VirtualFileUrls.getVirtualFileUrlManager(project);
    VirtualFileUrl fileUrl = fileUrlManager.fromPath(filePath);
    if (renamedOutputFileName != null) {
      addedEntity = diff.addEntity(FileCopyPackagingElementEntity.create(fileUrl, source, entityBuilder -> {
        entityBuilder.setRenamedOutputFileName(renamedOutputFileName);
        return Unit.INSTANCE;
      }));
    }
    else {
      addedEntity = diff.addEntity(FileCopyPackagingElementEntity.create(fileUrl, source));
    }
    diff.getMutableExternalMapping(PackagingExternalMapping.key).addMapping(addedEntity, this);
    return addedEntity;
  }

  @Nullable
  private String getMyRenamedOutputFileName() {
    if (myStorage == null) {
      return myRenamedOutputFileName;
    } else {
      FileCopyPackagingElementEntity entity = (FileCopyPackagingElementEntity)getThisEntity();
      String path = entity.getRenamedOutputFileName();
      if (!Objects.equals(myRenamedOutputFileName, path)) {
        myRenamedOutputFileName = path;
      }
      return path;
    }
  }
}
