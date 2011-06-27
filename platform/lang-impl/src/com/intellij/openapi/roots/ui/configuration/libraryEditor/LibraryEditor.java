/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface LibraryEditor {
  String getName();

  String[] getUrls(OrderRootType rootType);

  VirtualFile[] getFiles(OrderRootType rootType);

  void setName(String name);

  void addRoot(VirtualFile file, OrderRootType rootType);

  void addRoot(String url, OrderRootType rootType);

  void addJarDirectory(VirtualFile file, boolean recursive);

  void addJarDirectory(String url, boolean recursive);

  void addJarDirectory(VirtualFile file, boolean recursive, OrderRootType rootType);

  void addJarDirectory(String url, boolean recursive, OrderRootType rootType);

  void removeRoot(String url, OrderRootType rootType);

  void removeAllRoots();

  boolean hasChanges();

  boolean isJarDirectory(String url);

  boolean isJarDirectory(String url, OrderRootType rootType);

  boolean isValid(String url, OrderRootType orderRootType);

  LibraryProperties getProperties();

  @Nullable
  LibraryType getType();
}
