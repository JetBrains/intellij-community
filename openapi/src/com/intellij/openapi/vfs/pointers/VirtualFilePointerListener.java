/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs.pointers;

public interface VirtualFilePointerListener {
  void beforeValidityChanged(VirtualFilePointer[] pointers);
  void validityChanged(VirtualFilePointer[] pointers);
}
