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
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.LightFilePointer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author nik
 */
public class NewLibraryEditor extends LibraryEditorBase {
  private String myLibraryName;
  private final MultiMap<OrderRootType, LightFilePointer> myRoots;
  private final Set<LightFilePointer> myExcludedRoots;
  private final JarDirectories myJarDirectories = new JarDirectories();
  private LibraryType myType;
  private LibraryProperties myProperties;
  private boolean myKeepInvalidUrls = true;

  public NewLibraryEditor() {
    this(null, null);
  }

  public NewLibraryEditor(@Nullable LibraryType type, @Nullable LibraryProperties properties) {
    myType = type;
    myProperties = properties;
    myRoots = new MultiMap<>();
    myExcludedRoots = new LinkedHashSet<>();
  }

  public boolean isKeepInvalidUrls() {
    return myKeepInvalidUrls;
  }

  public void setKeepInvalidUrls(boolean keepInvalidUrls) {
    myKeepInvalidUrls = keepInvalidUrls;
  }

  @Override
  public Collection<OrderRootType> getOrderRootTypes() {
    return myRoots.keySet();
  }

  @Override
  @Nullable
  public LibraryType<?> getType() {
    return myType;
  }

  @Override
  public void setType(@NotNull LibraryType<?> type) {
    myType = type;
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

  @NotNull
  @Override
  public String[] getUrls(@NotNull OrderRootType rootType) {
    return pointersToUrls(myRoots.get(rootType));
  }

  private static String[] pointersToUrls(Collection<LightFilePointer> pointers) {
    List<String> urls = new ArrayList<>(pointers.size());
    for (LightFilePointer pointer : pointers) {
      urls.add(pointer.getUrl());
    }
    return ArrayUtil.toStringArray(urls);
  }

  @NotNull
  @Override
  public VirtualFile[] getFiles(@NotNull OrderRootType rootType) {
    List<VirtualFile> result = new ArrayList<>();
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
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @NotNull
  @Override
  public String[] getExcludedRootUrls() {
    return pointersToUrls(myExcludedRoots);
  }

  @Override
  public void setName(String name) {
    myLibraryName = name;
  }

  @Override
  public void addRoot(@NotNull VirtualFile file, @NotNull OrderRootType rootType) {
    myRoots.putValue(rootType, new LightFilePointer(file));
  }

  @Override
  public void addRoot(@NotNull String url, @NotNull OrderRootType rootType) {
    myRoots.putValue(rootType, new LightFilePointer(url));
  }

  @Override
  public void addJarDirectory(@NotNull VirtualFile file, boolean recursive, @NotNull OrderRootType rootType) {
    addJarDirectory(file.getUrl(), recursive, rootType);
  }

  @Override
  public void addExcludedRoot(@NotNull String url) {
    myExcludedRoots.add(new LightFilePointer(url));
  }

  @Override
  public void removeExcludedRoot(@NotNull String url) {
    myExcludedRoots.remove(new LightFilePointer(url));
  }

  @Override
  public void addJarDirectory(@NotNull final String url, boolean recursive, @NotNull OrderRootType rootType) {
    addRoot(url, rootType);
    myJarDirectories.add(rootType, url, recursive);
  }

  @Override
  public void removeRoot(@NotNull String url, @NotNull OrderRootType rootType) {
    myRoots.remove(rootType, new LightFilePointer(url));
    Iterator<LightFilePointer> iterator = myExcludedRoots.iterator();
    while (iterator.hasNext()) {
      LightFilePointer pointer = iterator.next();
      if (!isUnderRoots(pointer.getUrl())) {
        iterator.remove();
      }
    }
    myJarDirectories.remove(rootType, url);
  }

  private boolean isUnderRoots(@NotNull String url) {
    for (LightFilePointer pointer : myRoots.values()) {
      if (VfsUtilCore.isEqualOrAncestor(pointer.getUrl(), url)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hasChanges() {
    return true;
  }

  @Override
  public boolean isJarDirectory(@NotNull String url, @NotNull OrderRootType rootType) {
    return myJarDirectories.contains(rootType, url);
  }

  @Override
  public boolean isValid(@NotNull String url, @NotNull OrderRootType orderRootType) {
    final Collection<LightFilePointer> pointers = myRoots.get(orderRootType);
    for (LightFilePointer pointer : pointers) {
      if (pointer.getUrl().equals(url)) {
        return pointer.isValid();
      }
    }
    return false;
  }

  public void applyTo(@NotNull LibraryEx.ModifiableModelEx model) {
    model.setProperties(myProperties);
    exportRoots(model::getUrls, model::isValid, model::removeRoot, model::addRoot, model::addJarDirectory);
  }


  public void applyTo(@NotNull LibraryEditorBase editor) {
    editor.setProperties(myProperties);
    exportRoots(editor::getUrls, editor::isValid, editor::removeRoot, editor::addRoot, editor::addJarDirectory);
  }

  private void exportRoots(
    final Function<OrderRootType, String[]> getUrls,
    final BiFunction<String, OrderRootType, Boolean> isValid,
    final BiConsumer<String, OrderRootType> removeRoot,
    final BiConsumer<String, OrderRootType> addRoot,
    final TriConsumer<String, Boolean, OrderRootType> addJarDir) {

    // first, clean the target container optionally preserving invalid paths
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      for (String url : getUrls.apply(type)) {
        if (!myKeepInvalidUrls || isValid.apply(url, type)) {
          removeRoot.accept(url, type);
        }
      }
    }

    // apply editor's state to the target container
    for (OrderRootType type : myRoots.keySet()) {
      for (LightFilePointer pointer : myRoots.get(type)) {
        if (!myJarDirectories.contains(type, pointer.getUrl())) {
          addRoot.accept(pointer.getUrl(), type);
        }
      }
    }
    for (OrderRootType type : myJarDirectories.getRootTypes()) {
      for (String url : myJarDirectories.getDirectories(type)) {
        addJarDir.accept(url, myJarDirectories.isRecursive(type, url), type);
      }
    }
  }

  @FunctionalInterface
  private interface TriConsumer<T, U, P> {
    void accept(T t, U u, P p);
  }
}
