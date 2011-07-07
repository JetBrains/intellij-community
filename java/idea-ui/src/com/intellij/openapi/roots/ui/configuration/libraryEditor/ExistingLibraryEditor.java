/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

public class ExistingLibraryEditor extends LibraryEditorBase implements Disposable {
  private final Library myLibrary;
  private final LibraryEditorListener myListener;
  private String myLibraryName = null;
  private LibraryProperties myLibraryProperties;
  private Library.ModifiableModel myModel = null;

  public ExistingLibraryEditor(@NotNull Library library, @Nullable LibraryEditorListener listener) {
    myLibrary = library;
    myListener = listener;
  }

  public Library getLibrary() {
    return myLibrary;
  }

  @Override
  public String getName() {
    if (myLibraryName != null) {
      return myLibraryName;
    }
    return myLibrary.getName();
  }

  @Override
  public LibraryType getType() {
    return ((LibraryEx)myLibrary).getType();
  }

  @Override
  public LibraryProperties getProperties() {
    final LibraryType type = getType();
    if (type == null) return null;

    if (myLibraryProperties == null) {
      myLibraryProperties = type.createDefaultProperties();
      //noinspection unchecked
      myLibraryProperties.loadState(getOriginalProperties().getState());
    }
    return myLibraryProperties;
  }

  @Override
  public void setProperties(LibraryProperties properties) {
    myLibraryProperties = properties;
  }

  private LibraryProperties getOriginalProperties() {
    return ((LibraryEx)myLibrary).getProperties();
  }

  public void dispose() {
    if (myModel != null) {
      // dispose if wasn't committed
      Disposer.dispose(myModel);
    }
  }

  @Override
  public String[] getUrls(OrderRootType rootType) {
    if (myModel != null) {
      return myModel.getUrls(rootType);
    }
    return myLibrary.getUrls(rootType);
  }

  @Override
  public VirtualFile[] getFiles(OrderRootType rootType) {
    if (myModel != null) {
      return myModel.getFiles(rootType);
    }
    return myLibrary.getFiles(rootType);
  }

  @Override
  public void setName(String name) {
    String oldName = getModel().getName();
    myLibraryName = name;
    getModel().setName(name);
    if (myListener != null) {
      myListener.libraryRenamed(myLibrary, oldName, name);
    }
  }

  @Override
  public void addRoot(VirtualFile file, OrderRootType rootType) {
    getModel().addRoot(file, rootType);
  }

  @Override
  public void addRoot(String url, OrderRootType rootType) {
    getModel().addRoot(url, rootType);
  }

  @Override
  public void addJarDirectory(VirtualFile file, boolean recursive) {
    getModel().addJarDirectory(file, recursive);
  }

  @Override
  public void addJarDirectory(String url, boolean recursive) {
    getModel().addJarDirectory(url, recursive);
  }

  @Override
  public void addJarDirectory(VirtualFile file, boolean recursive, OrderRootType rootType) {
    getModel().addJarDirectory(file, recursive, rootType);
  }

  @Override
  public void addJarDirectory(String url, boolean recursive, OrderRootType rootType) {
    getModel().addJarDirectory(url, recursive, rootType);
  }

  @Override
  public void removeRoot(String url, OrderRootType rootType) {
    while (getModel().removeRoot(url, rootType)) ;
  }

  public void commit() {
    if (myModel != null) {
      if (myLibraryProperties != null) {
        ((LibraryEx.ModifiableModelEx)myModel).setProperties(myLibraryProperties);
      }
      myModel.commit();
      myModel = null;
      myLibraryName = null;
      myLibraryProperties = null;
    }
  }

  public Library.ModifiableModel getModel() {
    if (myModel == null) {
      myModel = myLibrary.getModifiableModel();
    }
    return myModel;
  }

  @Override
  public boolean hasChanges() {
    if (myModel != null && myModel.isChanged()) {
      return true;
    }
    return myLibraryProperties != null && !myLibraryProperties.equals(getOriginalProperties());
  }

  @Override
  public boolean isJarDirectory(String url, OrderRootType rootType) {
    if (myModel != null) {
      return myModel.isJarDirectory(url, rootType);
    }
    return myLibrary.isJarDirectory(url, rootType);
  }

  @Override
  public boolean isValid(final String url, final OrderRootType orderRootType) {
    if (myModel != null) {
      return myModel.isValid(url, orderRootType);
    }
    return myLibrary.isValid(url, orderRootType);
  }

  @Override
  public Collection<OrderRootType> getOrderRootTypes() {
    return Arrays.asList(OrderRootType.getAllTypes());
  }
}
