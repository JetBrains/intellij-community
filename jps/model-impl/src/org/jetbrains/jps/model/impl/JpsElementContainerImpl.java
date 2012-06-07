package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class JpsElementContainerImpl implements JpsElementContainer {
  private final Map<JpsElementKind<?>, JpsElement> myElements = new HashMap<JpsElementKind<?>, JpsElement>();
  private final @NotNull JpsModel myModel;
  private final @NotNull JpsEventDispatcher myEventDispatcher;
  private final @NotNull JpsParentElement myParent;

  public JpsElementContainerImpl(@NotNull JpsModel model,
                                 @NotNull JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    myModel = model;
    myEventDispatcher = eventDispatcher;
    myParent = parent;
  }

  public JpsElementContainerImpl(@NotNull JpsElementContainerImpl original, @NotNull JpsModel model,
                                 @NotNull JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    myModel = model;
    myEventDispatcher = eventDispatcher;
    myParent = parent;
    for (Map.Entry<JpsElementKind<?>, JpsElement> entry : original.myElements.entrySet()) {
      final JpsElementKind kind = entry.getKey();
      myElements.put(kind, entry.getValue().getBulkModificationSupport().createCopy(myModel, myEventDispatcher, myParent));
    }
  }

  @Override
  public <T extends JpsElement> T getChild(@NotNull JpsElementKind<T> kind) {
    //noinspection unchecked
    return (T)myElements.get(kind);
  }

  @NotNull
  @Override
  public <T extends JpsElement, P, K extends JpsElementKind<T> & JpsElementFactoryWithParameter<T, P>> T setChild(@NotNull K kind,
                                                                                                                  @NotNull P param) {
    final T child = kind.create(myModel, myEventDispatcher, myParent, param);
    return setChild(kind, child);
  }

  @NotNull
  @Override
  public <T extends JpsElement, K extends JpsElementKind<T> & JpsElementFactory<T>> T setChild(@NotNull K kind) {
    final T child = kind.create(myModel, myEventDispatcher, myParent);
    return setChild(kind, child);
  }

  @Override
  public <T extends JpsElement> T setChild(JpsElementKind<T> kind, T child) {
    myElements.put(kind, child);
    myEventDispatcher.fireElementAdded(child, kind);
    return child;
  }

  @Override
  public <T extends JpsElement> void removeChild(@NotNull JpsElementKind<T> kind) {
    //noinspection unchecked
    final T removed = (T)myElements.remove(kind);
    myEventDispatcher.fireElementRemoved(removed, kind);
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
      setChild(kind, (T)modifiedChild.getBulkModificationSupport().createCopy(myModel, myEventDispatcher, myParent));
    }
  }
}
