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
import com.intellij.openapi.roots.impl.libraries.JarDirectories;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.ui.LightFilePointer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class NewLibraryEditor extends LibraryEditorBase {
  private String myLibraryName;
  private final MultiMap<OrderRootType, LightFilePointer> myRoots;
  private final JarDirectories myJarDirectories = new JarDirectories();
  private final LibraryType myType;
  private LibraryProperties myProperties;

  public NewLibraryEditor() {
    this(null, null);
  }

  public NewLibraryEditor(LibraryType type, LibraryProperties properties) {
    myType = type;
    myProperties = properties;
    myRoots = new MultiMap<OrderRootType, LightFilePointer>();
  }

  @Override
  public Collection<OrderRootType> getOrderRootTypes() {
    return myRoots.keySet();
  }

  @Override
  @Nullable
  public LibraryType getType() {
    return myType;
  }

  @Override
  public LibraryProperties getProperties() {
    return myProperties;
  }

  @Override
  public void setProperties(LibraryProperties properties) {
    myProperties = properties;
  }

  @Override
  public String getName() {
    return myLibraryName;
  }

  @Override
  public String[] getUrls(OrderRootType rootType) {
    final Collection<LightFilePointer> pointers = myRoots.get(rootType);
    List<String> urls = new ArrayList<String>();
    for (LightFilePointer pointer : pointers) {
      urls.add(pointer.getUrl());
    }
    return ArrayUtil.toStringArray(urls);
  }

  @Override
  public VirtualFile[] getFiles(OrderRootType rootType) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (LightFilePointer pointer : myRoots.get(rootType)) {
      final VirtualFile file = pointer.getFile();
      if (file == null) {
        continue;
      }

      if (file.isDirectory()) {
        final String url = file.getUrl();
        if (myJarDirectories.contains(rootType, url)) {
          LibraryImpl.collectJarFiles(file, result, myJarDirectories.isRecursive(rootType, url));
          continue;
        }
      }
      result.add(file);
    }
    return VfsUtil.toVirtualFileArray(result);
  }

  @Override
  public void setName(String name) {
    myLibraryName = name;
  }

  @Override
  public void addRoot(VirtualFile file, OrderRootType rootType) {
    myRoots.putValue(rootType, new LightFilePointer(file));
  }

  @Override
  public void addRoot(String url, OrderRootType rootType) {
    myRoots.putValue(rootType, new LightFilePointer(url));
  }

  @Override
  public void addJarDirectory(VirtualFile file, boolean recursive) {
    addJarDirectory(file.getUrl(), recursive, OrderRootType.CLASSES);
  }

  @Override
  public void addJarDirectory(final String url, boolean recursive) {
    addJarDirectory(url, recursive, OrderRootType.CLASSES);
  }

  @Override
  public void addJarDirectory(VirtualFile file, boolean recursive, OrderRootType rootType) {
    addJarDirectory(file.getUrl(), recursive, rootType);
  }

  @Override
  public void addJarDirectory(final String url, boolean recursive, OrderRootType rootType) {
    addRoot(url, rootType);
    myJarDirectories.add(rootType, url, recursive);
  }

  @Override
  public void removeRoot(String url, OrderRootType rootType) {
    myRoots.removeValue(rootType, new LightFilePointer(url));
    myJarDirectories.remove(rootType, url);
  }

  @Override
  public boolean hasChanges() {
    return true;
  }

  @Override
  public boolean isJarDirectory(String url, OrderRootType rootType) {
    return myJarDirectories.contains(rootType, url);
  }

  @Override
  public boolean isValid(String url, OrderRootType orderRootType) {
    final Collection<LightFilePointer> pointers = myRoots.get(orderRootType);
    for (LightFilePointer pointer : pointers) {
      if (pointer.getUrl().equals(url)) {
        return pointer.isValid();
      }
    }
    return false;
  }

  public void apply(@NotNull LibraryEx.ModifiableModelEx model) {
    model.setName(myLibraryName);
    applyTo(model);
  }

  public void applyTo(LibraryEx.ModifiableModelEx model) {
    model.setProperties(myProperties);
    for (OrderRootType type : myRoots.keySet()) {
      for (LightFilePointer pointer : myRoots.get(type)) {
        model.addRoot(pointer.getUrl(), type);
      }
    }
    for (OrderRootType rootType : myJarDirectories.getRootTypes()) {
      for (String url : myJarDirectories.getDirectories(rootType)) {
        model.addJarDirectory(url, myJarDirectories.isRecursive(rootType, url), rootType);
      }
    }
  }

  public void applyTo(LibraryEditorBase editor) {
    editor.setProperties(myProperties);
    for (OrderRootType type : myRoots.keySet()) {
      for (LightFilePointer pointer : myRoots.get(type)) {
        editor.addRoot(pointer.getUrl(), type);
      }
    }
    for (OrderRootType rootType : myJarDirectories.getRootTypes()) {
      for (String url : myJarDirectories.getDirectories(rootType)) {
        editor.addJarDirectory(url, myJarDirectories.isRecursive(rootType, url), rootType);
      }
    }
  }
}
