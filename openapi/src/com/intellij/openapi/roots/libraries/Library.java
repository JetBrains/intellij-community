/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;

/**
 *  @author dsl
 */
public interface Library extends JDOMExternalizable {
  String getName();

  String[] getUrls(OrderRootType rootType);

  VirtualFile[] getFiles(OrderRootType rootType);

  /**
   * @deprecated
   */
  VirtualFilePointer[] getFilePointers(OrderRootType rootType);

  ModifiableModel getModifiableModel();

  LibraryTable getTable();

  RootProvider getRootProvider();

  interface ModifiableModel {
    String[] getUrls(OrderRootType rootType);

    void setName(String name);

    String getName();

    void addRoot(String url, OrderRootType rootType);

    void addRoot(VirtualFile file, OrderRootType rootType);

    void moveRootUp(String url, OrderRootType rootType);

    void moveRootDown(String url, OrderRootType rootType);

    boolean removeRoot(String url, OrderRootType rootType);

    void commit();

    VirtualFile[] getFiles(OrderRootType rootType);

    boolean isChanged();
  }

}
