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
package com.intellij.openapi.vfs.pointers;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author dsl
 */
public interface VirtualFilePointerContainer {
  void killAll();

  void add(@NotNull VirtualFile file);

  void add(@NotNull String url);

  void remove(@NotNull VirtualFilePointer pointer);

  @NotNull List<VirtualFilePointer> getList();

  void addAll(@NotNull VirtualFilePointerContainer that);

  @NotNull String[] getUrls();

  @NotNull VirtualFile[] getFiles();

  @NotNull VirtualFile[] getDirectories();

  @Nullable
  VirtualFilePointer findByUrl(@NotNull String url);

  void clear();

  int size();

  void readExternal(@NotNull Element rootChild, @NotNull String childElementName) throws InvalidDataException;

  void writeExternal(@NotNull Element element, @NotNull String childElementName);

  void moveUp(@NotNull String url);

  void moveDown(@NotNull String url);

  @NotNull VirtualFilePointerContainer clone(@NotNull Disposable parent);

  @NotNull VirtualFilePointerContainer clone(@NotNull Disposable parent, @Nullable VirtualFilePointerListener listener);
}
