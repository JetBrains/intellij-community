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
package org.jetbrains.jps.model.serialization;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;

/**
 * @author nik
 */
public abstract class JpsElementExtensionSerializerBase<E extends JpsElement> {
  private final String myConfigFileName;
  private final String myComponentName;

  protected JpsElementExtensionSerializerBase(@Nullable String configFileName, @NotNull String componentName) {
    myComponentName = componentName;
    myConfigFileName = configFileName;
  }

  @Nullable
  public String getConfigFileName() {
    return myConfigFileName;
  }

  @NotNull
  public String getComponentName() {
    return myComponentName;
  }

  public abstract void loadExtension(@NotNull E e, @NotNull Element componentTag);

  // called when no corresponding component tag was found in xml configs
  public void loadExtensionWithDefaultSettings(@NotNull E e) {
  }

  public abstract void saveExtension(@NotNull E e, @NotNull Element componentTag);
}
