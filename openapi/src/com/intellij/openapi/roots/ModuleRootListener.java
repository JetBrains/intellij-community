/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;

import java.util.EventListener;

/**
 *  @author dsl
 */
public interface ModuleRootListener extends EventListener{
  void beforeRootsChange(ModuleRootEvent event);
  void rootsChanged(ModuleRootEvent event);
}
