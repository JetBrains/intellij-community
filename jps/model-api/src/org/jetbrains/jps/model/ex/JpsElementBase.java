package org.jetbrains.jps.model.ex;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.JpsModel;

/**
 * @author nik
 */
public abstract class JpsElementBase<Self extends JpsElementBase<Self>> implements JpsElement, JpsElement.BulkModificationSupport<Self> {
  protected JpsElementBase myParent;

  protected JpsElementBase() {
  }

  public void setParent(@Nullable JpsElementBase<?> parent) {
    if (myParent != null && parent != null) {
      throw new AssertionError("Parent for " + this + " is already set: " + myParent);
    }
    myParent = parent;
  }

  protected void fireElementChanged() {
    final JpsEventDispatcher eventDispatcher = getEventDispatcher();
    if (eventDispatcher != null) {
      eventDispatcher.fireElementChanged(this);
    }
  }

  protected static void setParent(@NotNull JpsElement element, @Nullable JpsElementBase<?> parent) {
    ((JpsElementBase<?>)element).setParent(parent);
  }

  @Nullable
  protected JpsEventDispatcher getEventDispatcher() {
    if (myParent != null) {
      return myParent.getEventDispatcher();
    }
    return null;
  }

  @Nullable
  protected JpsModel getModel() {
    if (myParent != null) {
      return myParent.getModel();
    }
    return null;
  }

  @NotNull
  @Override
  public BulkModificationSupport<?> getBulkModificationSupport() {
    return this;
  }

  @NotNull
  public abstract Self createCopy();

  public abstract void applyChanges(@NotNull Self modified);

  public JpsElementBase getParent() {
    return myParent;
  }
}
