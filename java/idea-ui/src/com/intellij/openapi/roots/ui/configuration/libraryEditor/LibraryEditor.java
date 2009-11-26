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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class LibraryEditor implements Disposable {
  private final Library myLibrary;
  private final LibraryEditorListener myListener;
  private String myLibraryName = null;
  private Library.ModifiableModel myModel = null;

  public LibraryEditor(Library library, @NotNull LibraryEditorListener listener) {
    myLibrary = library;
    myListener = listener;
  }

  public String getName() {
    if (myLibraryName != null) {
      return myLibraryName;
    }
    return myLibrary.getName();
  }

  public void dispose() {
  }

  public String[] getUrls(OrderRootType rootType) {
    if (myModel != null) {
      return myModel.getUrls(rootType);
    }
    return myLibrary.getUrls(rootType);
  }

  public VirtualFile[] getFiles(OrderRootType rootType) {
    if (myModel != null) {
      return myModel.getFiles(rootType);
    }
    return myLibrary.getFiles(rootType);
  }

  public void setName(String name) {
    String oldName = getModel().getName();
    myLibraryName = name;
    getModel().setName(name);
    myListener.libraryRenamed(myLibrary, oldName, name);
  }

  public void addRoot(String url, OrderRootType rootType) {
    getModel().addRoot(url, rootType);
  }

  public void addRoot(VirtualFile file, OrderRootType rootType) {
    getModel().addRoot(file, rootType);
  }

  public void addJarDirectory(String url, boolean recursive) {
    getModel().addJarDirectory(url, recursive);
  }

  public void addJarDirectory(VirtualFile file, boolean recursive) {
    getModel().addJarDirectory(file, recursive);
  }

  public void removeRoot(String url, OrderRootType rootType) {
    while (getModel().removeRoot(url, rootType)) ;
  }

  public void commit() {
    if (myModel != null) {
      myModel.commit();
      myModel = null;
      myLibraryName = null;
    }
  }

  public Library.ModifiableModel getModel() {
    if (myModel == null) {
      myModel = myLibrary.getModifiableModel();
      Disposer.register(this, myModel);
    }
    return myModel;
  }

  public boolean hasChanges() {
    return myModel != null && myModel.isChanged();
  }
  
  public boolean isJarDirectory(String url) {
    if (myModel != null) {
      return myModel.isJarDirectory(url);
    }
    return myLibrary.isJarDirectory(url); 
  }
  
  public boolean allPathsValid(OrderRootType orderRootType) {
    if (myModel != null) {
      return ((LibraryEx.ModifiableModelEx)myModel).allPathsValid(orderRootType);
    }
    return ((LibraryEx)myLibrary).allPathsValid(orderRootType); 
  }

  public boolean isValid(final String url, final OrderRootType orderRootType) {
    if (myModel != null) {
      return myModel.isValid(url, orderRootType);
    }
    return myLibrary.isValid(url, orderRootType); 
  }
}
