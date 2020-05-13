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
package org.jetbrains.jps.model.artifact.elements;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a {@link JpsDirectoryPackagingElement directory} or an {@link JpsArchivePackagingElement archive} node in the output layout.
 */
public interface JpsCompositePackagingElement extends JpsPackagingElement {
  /**
   * @return list of nodes representing top-level content of this directory or archive
   */
  @NotNull
  List<JpsPackagingElement> getChildren();

  <E extends JpsPackagingElement> E addChild(@NotNull E child);

  void removeChild(@NotNull JpsPackagingElement child);
}
