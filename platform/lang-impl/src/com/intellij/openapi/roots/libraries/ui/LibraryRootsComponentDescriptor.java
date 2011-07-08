/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.libraries.ui;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public abstract class LibraryRootsComponentDescriptor {
  /**
   * Defines presentation for root type nodes in the library roots editor
   * @return custom presentation or {@code null} if default presentation should be used
   */
  @Nullable
  public abstract OrderRootTypePresentation getRootTypePresentation(@NotNull OrderRootType type);

  /**
   * @return descriptors for 'Attach' buttons in the library roots editor
   */
  @NotNull
  public abstract List<? extends AttachRootButtonDescriptor> createAttachButtons();

  /**
   * @return Array of root types supported by a library type associated with the roots
   *         component descriptor. All persistent root types are returned by default. 
   */
  public OrderRootType[] getRootTypes() {
    return OrderRootType.getAllTypes();
  }

}
