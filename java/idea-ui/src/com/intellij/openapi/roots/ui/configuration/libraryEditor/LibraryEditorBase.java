// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
  public void addRoots(@NotNull Collection<? extends OrderRoot> roots) {
    for (OrderRoot root : roots) {
      if (root.isJarDirectory()) {
        addJarDirectory(root.getFile(), false, root.getType());
      }
      else {
        addRoot(root.getFile(), root.getType());
      }
    }
  }

  @Override
  public @Nullable ProjectModelExternalSource getExternalSource() {
    return null;
  }
}
