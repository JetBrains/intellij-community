/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;

import java.util.EventListener;

/**
 *  Root provider for order entry
 *  @author dsl
 */
public interface RootProvider {
  String[] getUrls(OrderRootType rootType);

  interface RootSetChangedListener extends EventListener {
    void rootSetChanged(RootProvider wrapper);
  }

  void addRootSetChangedListener(RootSetChangedListener listener);
  void removeRootSetChangedListener(RootSetChangedListener listener);
}
