/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;



/**
 * @author dsl
 */
public class RootPolicy<R> {
  public R visitOrderEntry(OrderEntry orderEntry, R value) {
    return value;
  }

  public R visitModuleSourceOrderEntry(ModuleSourceOrderEntry moduleSourceOrderEntry, R value) {
    return visitOrderEntry(moduleSourceOrderEntry, value);
  }

  public R visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, R value) {
    return visitOrderEntry(libraryOrderEntry, value);
  }

  public R visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, R value) {
    return visitOrderEntry(moduleOrderEntry, value);
  }

  public R visitJdkOrderEntry(JdkOrderEntry jdkOrderEntry, R value) {
    return visitOrderEntry(jdkOrderEntry, value);
  }

  public R visitModuleJdkOrderEntry(ModuleJdkOrderEntry jdkOrderEntry, R value) {
    return visitJdkOrderEntry(jdkOrderEntry, value);
  }

  public R visitInheritedJdkOrderEntry(InheritedJdkOrderEntry inheritedJdkOrderEntry, R initialValue) {
    return visitJdkOrderEntry(inheritedJdkOrderEntry, initialValue);
  }


}
