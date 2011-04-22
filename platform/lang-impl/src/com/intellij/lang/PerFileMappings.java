/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.lang;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public interface PerFileMappings<T> {
  
  Map<VirtualFile, T> getMappings();

  void setMappings(Map<VirtualFile, T> mappings);

  Collection<T> getAvailableValues(final VirtualFile file);

  @Nullable
  T getMapping(final VirtualFile file);

  @Nullable
  T getDefaultMapping(@Nullable VirtualFile file);

  T chosenToStored(final VirtualFile file, final T value);

  boolean isSelectable(final T value);
}
