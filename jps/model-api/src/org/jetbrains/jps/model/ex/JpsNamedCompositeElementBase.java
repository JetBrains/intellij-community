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

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.JpsNamedElement;

public abstract class JpsNamedCompositeElementBase<Self extends JpsNamedCompositeElementBase<Self>> extends JpsCompositeElementBase<Self>
  implements JpsNamedElement {
  private @NlsSafe String myName;

  protected JpsNamedCompositeElementBase(@NlsSafe @NotNull String name) {
    super();
    myName = name;
  }

  protected JpsNamedCompositeElementBase(JpsNamedCompositeElementBase<Self> original) {
    super(original);
    myName = original.myName;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public void applyChanges(@NotNull Self modified) {
    super.applyChanges(modified);
    setName(modified.getName());
  }

  @Override
  public void setName(@NlsSafe @NotNull String name) {
    if (!myName.equals(name)) {
      String oldName = myName;
      myName = name;
      final JpsEventDispatcher eventDispatcher = getEventDispatcher();
      if (eventDispatcher != null) {
        eventDispatcher.fireElementRenamed(this, oldName, name);
      }
    }
  }
}
