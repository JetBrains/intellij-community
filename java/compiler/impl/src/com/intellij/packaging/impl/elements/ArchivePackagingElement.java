// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.elements;

import com.intellij.java.workspace.entities.ArchivePackagingElementEntity;
import com.intellij.java.workspace.entities.PackagingElementEntity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingExternalMapping;
import com.intellij.packaging.impl.ui.ArchiveElementPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.platform.workspace.storage.EntitySource;
import com.intellij.platform.workspace.storage.MutableEntityStorage;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import kotlin.Unit;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class ArchivePackagingElement extends CompositeElementWithManifest<ArchivePackagingElement> {
  @NonNls public static final String NAME_ATTRIBUTE = "name";
  private String myArchiveFileName;

  public ArchivePackagingElement() {
    super(PackagingElementFactoryImpl.ARCHIVE_ELEMENT_TYPE);
  }

  public ArchivePackagingElement(@NotNull String archiveFileName) {
    super(PackagingElementFactoryImpl.ARCHIVE_ELEMENT_TYPE);
    myArchiveFileName = archiveFileName;
  }

  @Override
  @NotNull
  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new ArchiveElementPresentation(this);
  }

  @Attribute(NAME_ATTRIBUTE)
  public @NlsSafe String getArchiveFileName() {
    return getMyArchiveName();
  }

  @NonNls @Override
  public String toString() {
    return "archive:" + getMyArchiveName();
  }

  @Override
  public ArchivePackagingElement getState() {
    return this;
  }

  public void setArchiveFileName(String archiveFileName) {
    renameArchive(archiveFileName);
  }

  @Override
  public String getName() {
    return getMyArchiveName();
  }

  @Override
  public void rename(@NotNull String newName) {
    renameArchive(newName);
  }

  private void renameArchive(String archiveFileName) {
    this.update(
      () -> myArchiveFileName = archiveFileName,
      (builder, entity) -> {
        builder.modifyEntity(ArchivePackagingElementEntity.Builder.class, entity, ent -> {
          ent.setFileName(archiveFileName);
          return Unit.INSTANCE;
        });
      }
    );
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof ArchivePackagingElement && ((ArchivePackagingElement)element).getArchiveFileName().equals(getMyArchiveName());
  }

  @Override
  public void loadState(@NotNull ArchivePackagingElement state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  public WorkspaceEntity getOrAddEntity(@NotNull MutableEntityStorage diff,
                                        @NotNull EntitySource source,
                                        @NotNull Project project) {
    WorkspaceEntity existingEntity = this.getExistingEntity(diff);
    if (existingEntity != null) return existingEntity;

    List<PackagingElementEntity> children = ContainerUtil.map(this.getChildren(), o -> {
      return (PackagingElementEntity)o.getOrAddEntity(diff, source, project);
    });

    Objects.requireNonNull(myArchiveFileName, "archiveFileName is not specified");
    var entity = diff.addEntity(ArchivePackagingElementEntity.create(myArchiveFileName, source, entityBuilder -> {
      entityBuilder.setChildren(children);
      return Unit.INSTANCE;
    }));
    diff.getMutableExternalMapping(PackagingExternalMapping.key).addMapping(entity, this);
    return entity;
  }

  private String getMyArchiveName() {
    if (myStorage == null) {
      return myArchiveFileName;
    } else {
      ArchivePackagingElementEntity entity = (ArchivePackagingElementEntity)getThisEntity();
      String fileName = entity.getFileName();
      if (!Objects.equals(fileName, myArchiveFileName)) {
        myArchiveFileName = fileName;
      }
      return fileName;
    }
  }
}
