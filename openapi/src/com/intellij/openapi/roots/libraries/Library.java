/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;

/**
 *  @author dsl
 */
public interface Library extends JDOMExternalizable, Disposable {
  String getName();

  String[] getUrls(OrderRootType rootType);

  VirtualFile[] getFiles(OrderRootType rootType);

  ModifiableModel getModifiableModel();

  LibraryTable getTable();

  RootProvider getRootProvider();

  boolean isJarDirectory(String url);
  
  boolean isValid(String url, OrderRootType rootType);
  
  interface ModifiableModel {
    String[] getUrls(OrderRootType rootType);

    void setName(String name);

    String getName();

    void addRoot(String url, OrderRootType rootType);
    
    void addJarDirectory(String url, boolean recursive);

    void addRoot(VirtualFile file, OrderRootType rootType);
    
    void addJarDirectory(VirtualFile file, boolean recursive);

    void moveRootUp(String url, OrderRootType rootType);

    void moveRootDown(String url, OrderRootType rootType);

    boolean removeRoot(String url, OrderRootType rootType);

    void commit();

    VirtualFile[] getFiles(OrderRootType rootType);

    boolean isChanged();
    
    boolean isJarDirectory(String url);
    
    boolean isValid(String url, OrderRootType rootType);
  }

  void readExternal(Element element) throws InvalidDataException;
  void writeExternal(Element element);
}
