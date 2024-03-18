// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.elements;

import com.intellij.java.workspace.entities.CustomPackagingElementEntity;
import com.intellij.java.workspace.entities.PackagingElementEntity;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.platform.workspace.storage.*;
import com.intellij.platform.workspace.storage.impl.VersionedEntityStorageOnBuilder;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Describes an element in artifact's output layout.
 *
 * @see com.intellij.packaging.artifacts.Artifact
 * @see PackagingElementFactory
 */
public abstract class PackagingElement<S> implements PersistentStateComponent<S> {
  private final PackagingElementType myType;

  protected @Nullable VersionedEntityStorage myStorage;
  protected Project myProject;
  protected Set<PackagingElement<?>> myElementsWithDiff;
  protected ElementInitializer myPackagingElementInitializer;

  protected PackagingElement(@NotNull PackagingElementType type) {
    myType = type;
  }

  @NotNull
  public abstract PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context);

  @NotNull
  public final PackagingElementType getType() {
    return myType;
  }

  public abstract boolean isEqualTo(@NotNull PackagingElement<?> element);

  @NotNull
  public PackagingElementOutputKind getFilesKind(PackagingElementResolvingContext context) {
    return PackagingElementOutputKind.OTHER;
  }

  /**
   * This method gets an entity from the diff mappings and create a new one for the current element
   */
  public WorkspaceEntity getOrAddEntity(@NotNull MutableEntityStorage diff,
                                        @NotNull EntitySource source,
                                        @NotNull Project project) {
    WorkspaceEntity existingEntity = getExistingEntity(diff);
    if (existingEntity != null) return existingEntity;

    S state = this.getState();
    String xmlTag = "";
    if (state != null) {
      xmlTag = JDOMUtil.write(XmlSerializer.serialize(state));
    }

    List<PackagingElementEntity> children = new ArrayList<>();
    if (this instanceof CompositePackagingElement<?>) {
      children.addAll(
        ContainerUtil.map(((CompositePackagingElement<Object>)this).getChildren(),
                          o -> (PackagingElementEntity)o.getOrAddEntity(diff, source, project))
      );
    }

    CustomPackagingElementEntity addedEntity =
      diff.addEntity(CustomPackagingElementEntity.create(this.getType().getId(), xmlTag, source, builder -> {
        builder.setChildren(children);
        return Unit.INSTANCE;
      }));

    diff.getMutableExternalMapping(PackagingExternalMapping.key).addMapping(addedEntity, this);
    return addedEntity;
  }

  protected @Nullable WorkspaceEntity getExistingEntity(MutableEntityStorage diff) {
    ExternalEntityMapping<PackagingElement<?>> mapping = diff.getExternalMapping(PackagingExternalMapping.key);
    return mapping.getFirstEntity(this);
  }

  public void setStorage(@NotNull VersionedEntityStorage storage, @NotNull Project project, Set<PackagingElement<?>> elementsWithDiff,
                         @NotNull ElementInitializer initializer) {
    // TODO set data to children
    myStorage = storage;
    myProject = project;
    myElementsWithDiff = elementsWithDiff;
    myPackagingElementInitializer = initializer;
  }

  public void updateStorage(@NotNull VersionedEntityStorage storage) {
    myStorage = storage;
  }

  public boolean hasStorage() {
    return myStorage != null;
  }

  public boolean storageIsStore() {
    return myStorage != null && !(myStorage instanceof VersionedEntityStorageOnBuilder);
  }

  protected void update(Runnable noStorageChange,
                        BiConsumer<? super MutableEntityStorage, ? super PackagingElementEntity> changeOnBuilder) {
    update(
      () -> {
        noStorageChange.run();
        return null;
      },
      (builder, element) -> {
        changeOnBuilder.accept(builder, element);
        return null;
      }
    );
  }

  protected <T> T update(Supplier<? extends T> noStorageChange,
                         BiFunction<? super MutableEntityStorage, ? super PackagingElementEntity, T> changeOnBuilder) {
    if (myStorage == null) {
      return noStorageChange.get();
    }
    else {
      if (!(myStorage instanceof VersionedEntityStorageOnBuilder)) {
        noStorageChange.get();
        throw new RuntimeException();
      }
      else {
        noStorageChange.get();
        MutableEntityStorage builder = ((VersionedEntityStorageOnBuilder)myStorage).getBase();
        MutableExternalEntityMapping<PackagingElement<?>> mapping = builder.getMutableExternalMapping(PackagingExternalMapping.key);
        PackagingElementEntity entity = (PackagingElementEntity)mapping.getFirstEntity(this);
        if (entity == null) {
          throw new RuntimeException("Cannot find an entity");
        }
        return changeOnBuilder.apply(builder, entity);
      }
    }
  }

  protected @NotNull PackagingElementEntity getThisEntity() {
    assert myStorage != null;
    EntityStorage base = myStorage.getBase();
    ExternalEntityMapping<PackagingElement<?>> externalMapping = base.getExternalMapping(PackagingExternalMapping.key);
    PackagingElementEntity entity = (PackagingElementEntity)externalMapping.getFirstEntity(this);
    if (entity == null) {
      throw new RuntimeException("Cannot find an entity");
    }
    return entity;
  }
}
