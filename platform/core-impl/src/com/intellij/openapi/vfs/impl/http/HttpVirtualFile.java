// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public abstract class HttpVirtualFile extends VirtualFile {
  public abstract @Nullable RemoteFileInfo getFileInfo();
}
