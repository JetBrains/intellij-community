/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs;

import java.util.EventListener;

public interface VirtualFileListener extends EventListener {
  void propertyChanged(VirtualFilePropertyEvent event);

  void contentsChanged(VirtualFileEvent event);

  void fileCreated(VirtualFileEvent event);

  void fileDeleted(VirtualFileEvent event);

  void fileMoved(VirtualFileMoveEvent event);

  void beforePropertyChange(VirtualFilePropertyEvent event);

  void beforeContentsChange(VirtualFileEvent event);

  void beforeFileDeletion(VirtualFileEvent event);

  void beforeFileMovement(VirtualFileMoveEvent event);
}
