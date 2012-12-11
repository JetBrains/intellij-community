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
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementContainer;

/**
 * @author nik
 */
public abstract class JpsCompositeElementBase<Self extends JpsCompositeElementBase<Self>> extends JpsElementBase<Self> implements JpsCompositeElement {
  protected final JpsElementContainerEx myContainer;

  protected JpsCompositeElementBase() {
    myContainer = JpsExElementFactory.getInstance().createContainer(this);
  }

  protected JpsCompositeElementBase(JpsCompositeElementBase<Self> original) {
    myContainer = JpsExElementFactory.getInstance().createContainerCopy(original.myContainer, this);
  }

  public void applyChanges(@NotNull Self modified) {
    myContainer.applyChanges(modified.myContainer);
  }

  @Override
  @NotNull
  public JpsElementContainer getContainer() {
    return myContainer;
  }
}
