/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;

public interface ContentIterator {
  /**
   * @return false if files processing should be stopped
   */
  boolean processFile(VirtualFile fileOrDir);
}
