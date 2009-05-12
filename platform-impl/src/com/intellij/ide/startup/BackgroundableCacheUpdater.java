/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.startup;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;

/**
 * @author peter
 */
public interface BackgroundableCacheUpdater extends CacheUpdater {

  boolean initiallyBackgrounded();

  boolean canBeSentToBackground(Collection<VirtualFile> remaining);

  void backgrounded(Collection<VirtualFile> remaining);

}
