/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;

/**
 *  @author dsl
 */
public interface ContentFolder extends Synthetic {
  /**
   * Returns virtual file for this source path's root.
   * @return null if source path is invalid
   */
  VirtualFile getFile();

  /**
   * @return this <code>ContentFolder</code>s {@link com.intellij.openapi.roots.ContentEntry}.
   */
  ContentEntry getContentEntry();

  String getUrl();
}
