/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
