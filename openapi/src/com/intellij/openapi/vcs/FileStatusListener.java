/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author mike
 */
public interface FileStatusListener {
  /**
   * Indicates that some file statuses were change. On this event client should recalculate all statuses
   * it's depenedend on.
   */
  void fileStatusesChanged();
  void fileStatusChanged(VirtualFile virtualFile);
}
