/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.localVcs;

/**
 * @author Mike
 */
public interface LvcsFileRevision extends LvcsRevision {
  byte[] getByteContent();
  long getByteLength();

  LvcsFileRevision getLastSavedRevision();
}
