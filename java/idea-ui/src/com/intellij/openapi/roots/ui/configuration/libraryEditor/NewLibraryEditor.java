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
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class NewLibraryEditor implements LibraryEditor {
  private String myLibraryName;
  private final Map<OrderRootType, VirtualFilePointerContainer> myRoots;
  private final Map<String, Boolean> myJarDirectories = new HashMap<String, Boolean>();

  public NewLibraryEditor() {
    myRoots = new HashMap<OrderRootType, VirtualFilePointerContainer>();
    for (OrderRootType rootType : OrderRootType.getAllTypes()) {
      myRoots.put(rootType, VirtualFilePointerManager.getInstance().createContainer(this));
    }
  }

  @Override
  public String getName() {
    return myLibraryName;
  }

  @Override
  public String[] getUrls(OrderRootType rootType) {
    return myRoots.get(rootType).getUrls();
  }

  @Override
  public VirtualFile[] getFiles(OrderRootType rootType) {
    return LibraryImpl.getRootFiles(myRoots.get(rootType), myJarDirectories);
  }

  @Override
  public void setName(String name) {
    myLibraryName = name;
  }

  @Override
  public void addRoot(VirtualFile file, OrderRootType rootType) {
    myRoots.get(rootType).add(file);
  }

  @Override
  public void addRoot(String url, OrderRootType rootType) {
    myRoots.get(rootType).add(url);
  }

  @Override
  public void addJarDirectory(VirtualFile file, boolean recursive) {
    myRoots.get(OrderRootType.CLASSES).add(file);
    myJarDirectories.put(file.getUrl(), recursive);
  }

  @Override
  public void removeRoot(String url, OrderRootType rootType) {
    final VirtualFilePointerContainer container = myRoots.get(rootType);
    final VirtualFilePointer pointer = container.findByUrl(url);
    if (pointer != null) {
      myJarDirectories.remove(pointer.getUrl());
      container.remove(pointer);
    }
  }

  @Override
  public boolean hasChanges() {
    return true;
  }

  @Override
  public boolean isJarDirectory(String url) {
    return myJarDirectories.containsKey(url);
  }

  @Override
  public boolean isValid(String url, OrderRootType orderRootType) {
    final VirtualFilePointer pointer = myRoots.get(orderRootType).findByUrl(url);
    return pointer != null && pointer.isValid();
  }

  @Override
  public void dispose() {
  }

  public void apply(@NotNull Library.ModifiableModel model) {
    model.setName(myLibraryName);
    for (Map.Entry<OrderRootType, VirtualFilePointerContainer> entry : myRoots.entrySet()) {
      for (String url : entry.getValue().getUrls()) {
        model.addRoot(url, entry.getKey());
      }
    }
    for (Map.Entry<String, Boolean> entry : myJarDirectories.entrySet()) {
      model.addJarDirectory(entry.getKey(), entry.getValue());
    }
  }
}
