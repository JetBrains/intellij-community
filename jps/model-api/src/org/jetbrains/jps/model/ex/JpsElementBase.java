// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.ex;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsModel;

public abstract class JpsElementBase<Self extends JpsElementBase<Self>> implements JpsElement, JpsElement.BulkModificationSupport<Self> {
  protected JpsElementBase myParent;

  protected JpsElementBase() {
  }

  @ApiStatus.Internal
  public void setParent(@Nullable JpsElementBase<?> parent) {
    if (myParent != null && parent != null) {
      throw new AssertionError("Parent for " + this + " is already set: " + myParent);
    }
    myParent = parent;
  }

  /**
   * @deprecated does nothing, all calls must be removed
   */
  @Deprecated(forRemoval = true)
  protected void fireElementChanged() {
  }

  @ApiStatus.Internal
  public static void setParent(@NotNull JpsElement element, @Nullable JpsElementBase<?> parent) {
    ((JpsElementBase<?>)element).setParent(parent);
  }

  protected @Nullable JpsModel getModel() {
    if (myParent != null) {
      return myParent.getModel();
    }
    return null;
  }

  @Override
  public @NotNull BulkModificationSupport<?> getBulkModificationSupport() {
    return this;
  }

  public JpsElementBase getParent() {
    return myParent;
  }
}
