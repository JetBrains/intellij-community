// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.elements;

import com.intellij.java.workspace.entities.DirectoryPackagingElementEntity;
import com.intellij.java.workspace.entities.PackagingElementEntity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingExternalMapping;
import com.intellij.packaging.impl.ui.DirectoryElementPresentation;
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

/**
 * classpath is used for exploded WAR and EJB directories under exploded EAR
 */
public class DirectoryPackagingElement extends CompositeElementWithManifest<DirectoryPackagingElement> {
  @NonNls public static final String NAME_ATTRIBUTE = "name";
  private String myDirectoryName;

  public DirectoryPackagingElement() {
    super(PackagingElementFactoryImpl.DIRECTORY_ELEMENT_TYPE);
  }

  public DirectoryPackagingElement(String directoryName) {
    super(PackagingElementFactoryImpl.DIRECTORY_ELEMENT_TYPE);
    myDirectoryName = directoryName;
  }

  @Override
  @NotNull
  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new DirectoryElementPresentation(this);
  }

  @Override
  public DirectoryPackagingElement getState() {
    return this;
  }

  @NonNls @Override
  public String toString() {
    return "dir:" + getMyDirectoryName();
  }

  @Attribute(NAME_ATTRIBUTE)
  public @NlsSafe String getDirectoryName() {
    return getMyDirectoryName();
  }

  public void setDirectoryName(String directoryName) {
    changeName(directoryName);
  }

  @Override
  public void rename(@NotNull String newName) {
    changeName(newName);
  }

  private void changeName(@NotNull String newName) {
    this.update(
      () -> myDirectoryName = newName,
      (builder, entity) -> {
        builder.modifyEntity(DirectoryPackagingElementEntity.Builder.class, entity, ent ->{
          ent.setDirectoryName(newName);
          return Unit.INSTANCE;
        });
      }
    );
  }

  @Override
  public String getName() {
    return getMyDirectoryName();
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof DirectoryPackagingElement && ((DirectoryPackagingElement)element).getDirectoryName().equals(getMyDirectoryName());
  }

  @Override
  public WorkspaceEntity getOrAddEntity(@NotNull MutableEntityStorage diff,
                                        @NotNull EntitySource source,
                                        @NotNull Project project) {
    WorkspaceEntity existingEntity = getExistingEntity(diff);
    if (existingEntity != null) return existingEntity;

    List<PackagingElementEntity> children = ContainerUtil.map(this.getChildren(), o -> {
      return (PackagingElementEntity)o.getOrAddEntity(diff, source, project);
    });

    Objects.requireNonNull(this.myDirectoryName, "directoryName is not specified");
    var entity = diff.addEntity(DirectoryPackagingElementEntity.create(this.myDirectoryName, source, entityBuilder -> {
      entityBuilder.setChildren(children);
      return Unit.INSTANCE;
    }));
    diff.getMutableExternalMapping(PackagingExternalMapping.key).addMapping(entity, this);
    return entity;
  }

  @Override
  public void loadState(@NotNull DirectoryPackagingElement state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  private String getMyDirectoryName() {
    if (myStorage == null) {
      return myDirectoryName;
    } else {
      DirectoryPackagingElementEntity entity = (DirectoryPackagingElementEntity)getThisEntity();
      String directoryName = entity.getDirectoryName();
      if (!Objects.equals(directoryName, myDirectoryName)) {
        myDirectoryName = directoryName;
      }
      return directoryName;
    }
  }
}
