// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.newvfs.BulkFileListenerBackgroundable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;

@Experimental
public class BulkVirtualFileListenerAdapterBackgroundable
  extends BulkVirtualFileListenerAdapter
  implements BulkFileListenerBackgroundable {

  public BulkVirtualFileListenerAdapterBackgroundable(@NotNull VirtualFileListener adapted) {
    super(adapted);
  }
}