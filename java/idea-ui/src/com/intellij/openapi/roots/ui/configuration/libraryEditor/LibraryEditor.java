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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author nik
 */
public interface LibraryEditor extends Disposable {
  String getName();

  String[] getUrls(OrderRootType rootType);

  VirtualFile[] getFiles(OrderRootType rootType);

  void setName(String name);

  void addRoot(VirtualFile file, OrderRootType rootType);

  void addRoot(String url, OrderRootType rootType);

  void addJarDirectory(VirtualFile file, boolean recursive);

  void removeRoot(String url, OrderRootType rootType);

  boolean hasChanges();

  boolean isJarDirectory(String url);

  boolean isValid(String url, OrderRootType orderRootType);
}
