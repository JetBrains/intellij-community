package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class JpsElementContainerImpl implements JpsElementContainer {
  private final Map<JpsElementKind<?>, JpsElement> myElements = new HashMap<JpsElementKind<?>, JpsElement>();
  private final @NotNull JpsCompositeElementBase<?> myParent;

  public JpsElementContainerImpl(@NotNull JpsCompositeElementBase<?> parent) {
    myParent = parent;
  }

  public JpsElementContainerImpl(@NotNull JpsElementContainerImpl original, @NotNull JpsCompositeElementBase<?> parent) {
    myParent = parent;
    for (Map.Entry<JpsElementKind<?>, JpsElement> entry : original.myElements.entrySet()) {
      final JpsElementKind kind = entry.getKey();
      final JpsElement copy = entry.getValue().getBulkModificationSupport().createCopy();
      JpsElementBase.setParent(copy, myParent);
      myElements.put(kind, copy);
    }
  }

  @Override
  public <T extends JpsElement> T getChild(@NotNull JpsElementKind<T> kind) {
    //noinspection unchecked
    return (T)myElements.get(kind);
  }

  @NotNull
  @Override
  public <T extends JpsElement, P, K extends JpsElementKind<T> & JpsElementParameterizedCreator<T, P>> T setChild(@NotNull K kind,
                                                                                                                  @NotNull P param) {
    final T child = kind.create(param);
    return setChild(kind, child);
  }

  @NotNull
  @Override
  public <T extends JpsElement, K extends JpsElementKind<T> & JpsElementCreator<T>> T setChild(@NotNull K kind) {
    final T child = kind.create();
    return setChild(kind, child);
  }

  @NotNull
  @Override
  public <T extends JpsElement, K extends JpsElementKind<T> & JpsElementCreator<T>> T getOrSetChild(@NotNull K kind) {
    final T child = getChild(kind);
    if (child == null) {
      return setChild(kind);
    }
    return child;
  }

  @Override
  public <T extends JpsElement> T setChild(JpsElementKind<T> kind, T child) {
    myElements.put(kind, child);
    JpsElementBase.setParent(child, myParent);
    final JpsEventDispatcher eventDispatcher = getEventDispatcher();
    if (eventDispatcher != null) {
      eventDispatcher.fireElementAdded(child, kind);
    }
    return child;
  }

  @Override
  public <T extends JpsElement> void removeChild(@NotNull JpsElementKind<T> kind) {
    //noinspection unchecked
    final T removed = (T)myElements.remove(kind);
    final JpsEventDispatcher eventDispatcher = getEventDispatcher();
    if (eventDispatcher != null) {
      eventDispatcher.fireElementRemoved(removed, kind);
    }
    JpsElementBase.setParent(removed, null);
  }

  public void applyChanges(@NotNull JpsElementContainerImpl modified) {
    for (JpsElementKind<?> kind : myElements.keySet()) {
      applyChanges(kind, modified);
    }
    for (JpsElementKind<?> kind : modified.myElements.keySet()) {
      if (!myElements.containsKey(kind)) {
        applyChanges(kind, modified);
      }
    }
  }

  private <T extends JpsElement> void applyChanges(JpsElementKind<T> kind, JpsElementContainerImpl modified) {
    final T child = getChild(kind);
    final T modifiedChild = modified.getChild(kind);
    if (child != null && modifiedChild != null) {
      final JpsElement.BulkModificationSupport modificationSupport = child.getBulkModificationSupport();
      //noinspection unchecked
      modificationSupport.applyChanges(modifiedChild);
    }
    else if (modifiedChild == null) {
      removeChild(kind);
    }
    else {
      //noinspection unchecked
      setChild(kind, (T)modifiedChild.getBulkModificationSupport().createCopy());
    }
  }

  @Nullable
  private JpsEventDispatcher getEventDispatcher() {
    return myParent.getEventDispatcher();
  }
}
