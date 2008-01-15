package com.intellij.openapi.roots.impl;

import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;

/**
 *  @author dsl
 */
public interface ClonableOrderEntry {
  OrderEntry cloneEntry(RootModelImpl rootModel, ProjectRootManagerImpl projectRootManager, VirtualFilePointerManager filePointerManager);
}
