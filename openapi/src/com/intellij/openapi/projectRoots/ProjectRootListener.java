/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.projectRoots;

import java.util.EventListener;

public interface ProjectRootListener extends EventListener {
  void rootsChanged();
}
