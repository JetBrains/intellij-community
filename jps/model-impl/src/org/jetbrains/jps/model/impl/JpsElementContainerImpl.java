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
  private final Map<JpsElementChildRole<?>, JpsElement> myElements = new HashMap<JpsElementChildRole<?>, JpsElement>();
  private final @NotNull JpsCompositeElementBase<?> myParent;

  public JpsElementContainerImpl(@NotNull JpsCompositeElementBase<?> parent) {
    myParent = parent;
  }

  public JpsElementContainerImpl(@NotNull JpsElementContainerImpl original, @NotNull JpsCompositeElementBase<?> parent) {
    myParent = parent;
    for (Map.Entry<JpsElementChildRole<?>, JpsElement> entry : original.myElements.entrySet()) {
      final JpsElementChildRole role = entry.getKey();
      final JpsElement copy = entry.getValue().getBulkModificationSupport().createCopy();
      JpsElementBase.setParent(copy, myParent);
      myElements.put(role, copy);
    }
  }

  @Override
  public <T extends JpsElement> T getChild(@NotNull JpsElementChildRole<T> role) {
    //noinspection unchecked
    return (T)myElements.get(role);
  }

  @NotNull
  @Override
  public <T extends JpsElement, P, K extends JpsElementChildRole<T> & JpsElementParameterizedCreator<T, P>> T setChild(@NotNull K role,
                                                                                                                  @NotNull P param) {
    final T child = role.create(param);
    return setChild(role, child);
  }

  @NotNull
  @Override
  public <T extends JpsElement, K extends JpsElementChildRole<T> & JpsElementCreator<T>> T setChild(@NotNull K role) {
    final T child = role.create();
    return setChild(role, child);
  }

  @NotNull
  @Override
  public <T extends JpsElement, K extends JpsElementChildRole<T> & JpsElementCreator<T>> T getOrSetChild(@NotNull K role) {
    final T child = getChild(role);
    if (child == null) {
      return setChild(role);
    }
    return child;
  }

  @Override
  public <T extends JpsElement> T setChild(JpsElementChildRole<T> role, T child) {
    myElements.put(role, child);
    JpsElementBase.setParent(child, myParent);
    final JpsEventDispatcher eventDispatcher = getEventDispatcher();
    if (eventDispatcher != null) {
      eventDispatcher.fireElementAdded(child, role);
    }
    return child;
  }

  @Override
  public <T extends JpsElement> void removeChild(@NotNull JpsElementChildRole<T> role) {
    //noinspection unchecked
    final T removed = (T)myElements.remove(role);
    if (removed == null) return;
    final JpsEventDispatcher eventDispatcher = getEventDispatcher();
    if (eventDispatcher != null) {
      eventDispatcher.fireElementRemoved(removed, role);
    }
    JpsElementBase.setParent(removed, null);
  }

  public void applyChanges(@NotNull JpsElementContainerImpl modified) {
    for (JpsElementChildRole<?> role : myElements.keySet()) {
      applyChanges(role, modified);
    }
    for (JpsElementChildRole<?> role : modified.myElements.keySet()) {
      if (!myElements.containsKey(role)) {
        applyChanges(role, modified);
      }
    }
  }

  private <T extends JpsElement> void applyChanges(JpsElementChildRole<T> role, JpsElementContainerImpl modified) {
    final T child = getChild(role);
    final T modifiedChild = modified.getChild(role);
    if (child != null && modifiedChild != null) {
      final JpsElement.BulkModificationSupport modificationSupport = child.getBulkModificationSupport();
      //noinspection unchecked
      modificationSupport.applyChanges(modifiedChild);
    }
    else if (modifiedChild == null) {
      removeChild(role);
    }
    else {
      //noinspection unchecked
      setChild(role, (T)modifiedChild.getBulkModificationSupport().createCopy());
    }
  }

  @Nullable
  private JpsEventDispatcher getEventDispatcher() {
    return myParent.getEventDispatcher();
  }
}
