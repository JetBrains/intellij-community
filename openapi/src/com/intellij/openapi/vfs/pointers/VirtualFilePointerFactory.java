/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs.pointers;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author dsl
 */
public interface VirtualFilePointerFactory {
  VirtualFilePointer create(VirtualFile file);
  VirtualFilePointer create(String url);
  VirtualFilePointer duplicate(VirtualFilePointer virtualFilePointer);
}
