/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

/**
 * @author dyoma
 */
public interface ModificationTracker {
  long getModificationCount();

  ModificationTracker EVER_CHANGED = new ModificationTracker() {
    private long myCounter = 0;
    public long getModificationCount() {
      return myCounter++;
    }
  };
}
