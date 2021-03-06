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
package org.jetbrains.jps.model.module;

import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementType;
import org.jetbrains.jps.model.JpsElementTypeWithDefaultProperties;

/**
 * Represents a type of source roots of modules in JPS model.
 *
 * <p>
 * Use {@link org.jetbrains.jps.model.ex.JpsElementTypeBase} as a base class for implementations of this interface. In order to support
 * loading and saving of custom source root types, provide an implementation of {@link org.jetbrains.jps.model.serialization.module.JpsModuleSourceRootPropertiesSerializer}.
 * if you want to allow users add and remove roots of the custom type in Project Structure dialog, provide an implementation of
 * {@link com.intellij.openapi.roots.ui.configuration.ModuleSourceRootEditHandler} extension point.
 * </p>
 */
public interface JpsModuleSourceRootType<P extends JpsElement> extends JpsElementType<P>, JpsElementTypeWithDefaultProperties<P> {

  /**
   * Returns {@code true} if roots of this type are supposed to contain test sources only. This information is used by the IDE to show files
   * accordingly, process them during analysis only if 'Include test source' option is enabled, etc.
   */
  default boolean isForTests() {
    return false;
  }
}
