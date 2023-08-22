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
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.ex.JpsElementReferenceBase;

public final class JpsGlobalElementReference extends JpsElementReferenceBase<JpsGlobalElementReference, JpsGlobal> {
  @Override
  public JpsGlobal resolve() {
    final JpsModel model = getModel();
    return model != null ? model.getGlobal() : null;
  }

  @NotNull
  @Override
  public JpsGlobalElementReference createCopy() {
    return new JpsGlobalElementReference();
  }

  @Override
  public void applyChanges(@NotNull JpsGlobalElementReference modified) {
  }

  @Override
  public String toString() {
    return "global ref";
  }
}
