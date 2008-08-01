/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

  void add(VirtualFile file);

  void add(String url);

  void remove(VirtualFilePointer pointer);

  List<VirtualFilePointer> getList();

  void addAll(VirtualFilePointerContainer that);

  String[] getUrls();

  VirtualFile[] getFiles();

  VirtualFile[] getDirectories();

  @Nullable
  VirtualFilePointer findByUrl(String url);

  void clear();

  int size();

  Object get(int index);

  void readExternal(Element rootChild, String childElementName) throws InvalidDataException;

  void writeExternal(Element element, String childElementName);

  void moveUp(String url);

  void moveDown(String url);

  VirtualFilePointerContainer clone(@NotNull Disposable parent);

  VirtualFilePointerContainer clone(@NotNull Disposable parent, VirtualFilePointerListener listener);
}
