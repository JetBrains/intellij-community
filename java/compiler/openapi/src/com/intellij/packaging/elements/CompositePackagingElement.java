// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.elements;

import com.intellij.java.workspace.entities.CompositePackagingElementEntity;
import com.intellij.java.workspace.entities.PackagingElementEntity;
import com.intellij.platform.workspace.storage.ExternalEntityMapping;
import com.intellij.platform.workspace.storage.MutableExternalEntityMapping;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.platform.workspace.storage.impl.VersionedEntityStorageOnBuilder;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Pair;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public abstract class CompositePackagingElement<S> extends PackagingElement<S> implements RenameablePackagingElement {
  private final List<PackagingElement<?>> myChildren = new ArrayList<>();
  private List<PackagingElement<?>> myUnmodifiableChildren;

  protected CompositePackagingElement(PackagingElementType type) {
    super(type);
  }

  public <T extends PackagingElement<?>> T addOrFindChild(@NotNull T child) {
    return this.update(
      () -> myAddOrFindChild(child),
      (builder, packagingElementEntity) -> {
        MutableExternalEntityMapping<PackagingElement<?>> mapping = builder.getMutableExternalMapping(PackagingExternalMapping.key);
        CompositePackagingElementEntity entity = (CompositePackagingElementEntity)packagingElementEntity;
        List<? extends PackagingElement<?>> children = ContainerUtil.map(entity.getChildren().iterator(), o -> {
          PackagingElement<?> data = mapping.getDataByEntity(o);
          return Objects
            .requireNonNullElseGet(data, () -> (PackagingElement<?>)myPackagingElementInitializer.initialize(o, myProject, builder));
        });
        for (PackagingElement<?> element : children) {
          if (element.isEqualTo(child)) {
            if (element instanceof CompositePackagingElement) {
              final List<PackagingElement<?>> childrenOfChild = ((CompositePackagingElement<?>)child).getChildren();
              ((CompositePackagingElement<?>)element).addOrFindChildren(childrenOfChild);
            }

            // Set correct storage if needed
            setStorageForPackagingElement(element);
            //noinspection unchecked
            return (T) element;
          }
        }
        // TODO not sure if the entity source is correct
        PackagingElementEntity childEntity = (PackagingElementEntity)child.getOrAddEntity(builder, entity.getEntitySource(), myProject);
        builder.modifyEntity(CompositePackagingElementEntity.Builder.class, entity, o -> {
          List<PackagingElementEntity> mutableList = new ArrayList<>(o.getChildren());
          mutableList.add(childEntity);
          o.setChildren(mutableList);
          return Unit.INSTANCE;
        });
        // Set storage for the new child
        setStorageForPackagingElement(child);
        return child;
      }
    );
  }

  private <T extends PackagingElement<?>> T myAddOrFindChild(@NotNull T child) {
    for (PackagingElement<?> element : myChildren) {
      if (element.isEqualTo(child)) {
        if (element instanceof CompositePackagingElement) {
          final List<PackagingElement<?>> children = ((CompositePackagingElement<?>)child).getChildren();
          ((CompositePackagingElement<?>)element).addOrFindChildren(children);
        }
        //noinspection unchecked
        return (T) element;
      }
    }
    myChildren.add(child);
    return child;
  }

  public void addFirstChild(@NotNull PackagingElement<?> child) {
    this.update(
      () -> myAddFirstChild(child),
      (builder, packagingElementEntity) -> {
        MutableExternalEntityMapping<PackagingElement<?>> mapping = builder.getMutableExternalMapping(PackagingExternalMapping.key);
        CompositePackagingElementEntity entity = (CompositePackagingElementEntity)packagingElementEntity;
        List<Pair<PackagingElementEntity, PackagingElement<?>>> pairs =
          new ArrayList<>(ContainerUtil.map(entity.getChildren().iterator(), o -> {
            PackagingElement<?> data = mapping.getDataByEntity(o);
            if (data == null) {
              return new Pair<>(o, myPackagingElementInitializer.initialize(o, myProject, builder));
            }
            return new Pair<>(o, data);
          }));
        PackagingElementEntity childEntity = (PackagingElementEntity)child.getOrAddEntity(builder, entity.getEntitySource(), myProject);
        pairs.add(0, new Pair<>(childEntity, child));
        for (int i = 1; i < pairs.size(); i++) {
          Pair<PackagingElementEntity, PackagingElement<?>> pair = pairs.get(i);
          PackagingElement<?> element = pair.getSecond();
          if (element.isEqualTo(child)) {
            if (element instanceof CompositePackagingElement<?>) {
              ((CompositePackagingElement<?>)child).addOrFindChildren(((CompositePackagingElement<?>)element).getChildren());
            }
            pairs.remove(i);
            break;
          }
        }
        List<PackagingElementEntity> newChildren = ContainerUtil.map(pairs, o -> o.getFirst());
        //noinspection unchecked
        builder.modifyEntity(CompositePackagingElementEntity.Builder.class, entity, o -> {
          //noinspection unchecked
          o.setChildren(newChildren);
          return Unit.INSTANCE;
        });
        // Set storage for the new child
        setStorageForPackagingElement(child);
      }
    );
  }

  private void myAddFirstChild(@NotNull PackagingElement<?> child) {
    myChildren.add(0, child);
    for (int i = 1; i < myChildren.size(); i++) {
      PackagingElement<?> element = myChildren.get(i);
      if (element.isEqualTo(child)) {
        if (element instanceof CompositePackagingElement<?>) {
          ((CompositePackagingElement<?>)child).addOrFindChildren(((CompositePackagingElement<?>)element).getChildren());
        }
        myChildren.remove(i);
        break;
      }
    }
  }

  public List<? extends PackagingElement<?>> addOrFindChildren(Collection<? extends PackagingElement<?>> children) {
    List<PackagingElement<?>> added = new ArrayList<>();
    for (PackagingElement<?> child : children) {
      added.add(addOrFindChild(child));
    }
    return added;
  }

  @Nullable
  public PackagingElement<?> moveChild(int index, int direction) {
    return this.update(
      () -> myMove(index, direction, myChildren),
      (builder, packagingElementEntity) -> {
        CompositePackagingElementEntity entity = (CompositePackagingElementEntity)packagingElementEntity;
        ArrayList<PackagingElementEntity> children = new ArrayList<>(ContainerUtil.collect(entity.getChildren().iterator()));
        PackagingElementEntity entityToReturn = myMove(index, direction, children);

        //noinspection unchecked
        builder.modifyEntity(CompositePackagingElementEntity.Builder.class, entity, o -> {
          //noinspection unchecked
          o.setChildren(children);
          return Unit.INSTANCE;
        });

        if (entityToReturn == null) {
          return null;
        }
        MutableExternalEntityMapping<PackagingElement<?>> mapping = builder.getMutableExternalMapping(PackagingExternalMapping.key);
        PackagingElement<?> objectToReturn = mapping.getDataByEntity(entityToReturn);
        if (objectToReturn == null) {
          return myPackagingElementInitializer.initialize(entityToReturn, myProject, builder);
        }
        return objectToReturn;
      }
    );
  }

  @Nullable
  private static <T> T myMove(int index, int direction, List<T> elements) {
    int target = index + direction;
    if (0 <= index && index < elements.size() && 0 <= target && target < elements.size()) {
      final T element1 = elements.get(index);
      final T element2 = elements.get(target);
      elements.set(index, element2);
      elements.set(target, element1);
      return element1;
    }
    return null;
  }

  public void removeChild(@NotNull PackagingElement<?> child) {
    this.update(
      () -> myChildren.remove(child),
      (builder, packagingElementEntity) -> {
        MutableExternalEntityMapping<PackagingElement<?>> mapping = builder.getMutableExternalMapping(PackagingExternalMapping.key);
        WorkspaceEntity entity = mapping.getFirstEntity(child);
        if (entity != null) {
          builder.removeEntity(entity);
        }
      }
    );
  }

  public void removeChildren(@NotNull Collection<? extends PackagingElement<?>> children) {
    this.update(
      () -> myChildren.removeAll(children),
      (builder, packagingElementEntity) -> {
        MutableExternalEntityMapping<PackagingElement<?>> mapping = builder.getMutableExternalMapping(PackagingExternalMapping.key);
        children.stream()
          .map(o -> mapping.getFirstEntity(o))
          .filter(Objects::nonNull)
          .forEach(o -> builder.removeEntity(o));
      }
    );
  }

  @NotNull
  public List<PackagingElement<?>> getChildren() {
    if (myStorage == null) {
      return myGetChildren();
    }
    else {
      ExternalEntityMapping<PackagingElement<?>> mapping = myStorage.getBase().getExternalMapping(PackagingExternalMapping.key);
      PackagingElementEntity packagingElementEntity = (PackagingElementEntity)mapping.getFirstEntity(this);
      if (packagingElementEntity == null) {
        throw new RuntimeException(
          this.getClass().getName() + " - " + myStorage.getBase().getClass().getName() + " - " + myStorage.getClass().getName());
      }
      if (packagingElementEntity instanceof CompositePackagingElementEntity entity) {
        return ContainerUtil.map(entity.getChildren().iterator(), o -> {
          PackagingElement<?> data = mapping.getDataByEntity(o);
          if (data == null) {
            data = (PackagingElement<?>)myPackagingElementInitializer.initialize(o, myProject, myStorage.getBase());
          }
          setStorageForPackagingElement(data);
          return data;
        });
      }
      else {
        throw new RuntimeException("Expected composite element here");
      }
    }
  }

  private List<PackagingElement<?>> myGetChildren() {
    if (myUnmodifiableChildren == null) {
      myUnmodifiableChildren = Collections.unmodifiableList(myChildren);
    }
    return myUnmodifiableChildren;
  }

  @TestOnly
  public List<PackagingElement<?>> getNonWorkspaceModelChildren() {
    return myChildren;
  }

  @Override
  public boolean canBeRenamed() {
    return true;
  }

  public void removeAllChildren() {
    this.update(
      () -> myChildren.clear(),
      (builder, packagingElementEntity) -> {
        CompositePackagingElementEntity entity = (CompositePackagingElementEntity)packagingElementEntity;
        // I just don't understand what to do to avoid this warning
        //noinspection unchecked
        builder.modifyEntity(CompositePackagingElementEntity.Builder.class, entity, o -> {
          //noinspection unchecked
          o.setChildren(new ArrayList<>());
          return Unit.INSTANCE;
        });
      }
    );
  }

  @Nullable
  public CompositePackagingElement<?> findCompositeChild(@NotNull String name) {
    for (PackagingElement<?> child : getChildren()) {
      if (child instanceof CompositePackagingElement && name.equals(((CompositePackagingElement<?>)child).getName())) {
        return (CompositePackagingElement)child;
      }
    }
    return null;
  }

  private void setStorageForPackagingElement(PackagingElement<?> packagingElement) {
    boolean storageIsDiff = myStorage instanceof VersionedEntityStorageOnBuilder;
    if (storageIsDiff && (packagingElement.storageIsStore() || !packagingElement.hasStorage())) {
      packagingElement.setStorage(myStorage, myProject, myElementsWithDiff, myPackagingElementInitializer);
      myElementsWithDiff.add(packagingElement);
    }
  }
}
