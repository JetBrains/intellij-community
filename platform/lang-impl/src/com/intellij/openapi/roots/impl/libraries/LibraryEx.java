package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.RootModelImpl;
import com.intellij.openapi.roots.libraries.Library;

/**
 *  @author dsl
 */
public interface LibraryEx extends Library {
  Library cloneLibrary(RootModelImpl rootModel);

  boolean allPathsValid(OrderRootType type);

  boolean isDisposed();

  interface ModifiableModelEx extends ModifiableModel {
    boolean allPathsValid(OrderRootType type);
  }
}
