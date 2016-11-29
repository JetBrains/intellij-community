/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public abstract class LibraryEditorBase implements LibraryEditor {
  @Override
  public void removeAllRoots() {
    final List<OrderRootType> types = new ArrayList<>(getOrderRootTypes());
    for (OrderRootType type : types) {
      final String[] urls = getUrls(type);
      for (String url : urls) {
        removeRoot(url, type);
      }
    }
  }

  protected abstract Collection<OrderRootType> getOrderRootTypes();

  public abstract void setProperties(LibraryProperties properties);

  public abstract void setType(@NotNull LibraryType<?> type);

  @Override
  public void addRoots(Collection<? extends OrderRoot> roots) {
    for (OrderRoot root : roots) {
      if (root.isJarDirectory()) {
        addJarDirectory(root.getFile(), false, root.getType());
      }
      else {
        addRoot(root.getFile(), root.getType());
      }
    }
  }
}
