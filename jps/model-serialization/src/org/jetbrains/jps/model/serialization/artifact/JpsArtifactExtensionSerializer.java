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
package org.jetbrains.jps.model.serialization.artifact;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;

/**
 * @author nik
 */
public abstract class JpsArtifactExtensionSerializer<E extends JpsElement> {
  private final JpsElementChildRole<E> myRole;
  private final String myId;

  protected JpsArtifactExtensionSerializer(String id, JpsElementChildRole<E> role) {
    myId = id;
    myRole = role;
  }

  public JpsElementChildRole<E> getRole() {
    return myRole;
  }

  public String getId() {
    return myId;
  }

  public abstract E loadExtension(@Nullable Element optionsTag);

  public abstract void saveExtension(@NotNull E extension, @NotNull Element optionsTag);
}
