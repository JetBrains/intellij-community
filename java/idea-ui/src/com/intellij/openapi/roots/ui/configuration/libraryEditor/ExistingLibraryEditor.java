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

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public class ExistingLibraryEditor implements LibraryEditor {
  private final Library myLibrary;
  private final LibraryEditorListener myListener;
  private String myLibraryName = null;
  private Library.ModifiableModel myModel = null;

  public ExistingLibraryEditor(Library library, @Nullable LibraryEditorListener listener) {
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
    }
    return myModel;
  }

  @Override
  public boolean hasChanges() {
    return myModel != null && myModel.isChanged();
  }
  
  @Override
  public boolean isJarDirectory(String url) {
    if (myModel != null) {
      return myModel.isJarDirectory(url);
    }
    return myLibrary.isJarDirectory(url); 
  }

  @Override
  public boolean isValid(final String url, final OrderRootType orderRootType) {
    if (myModel != null) {
      return myModel.isValid(url, orderRootType);
    }
    return myLibrary.isValid(url, orderRootType); 
  }
}
