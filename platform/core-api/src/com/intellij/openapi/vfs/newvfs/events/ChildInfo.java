// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.util.io.FileAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** An internal class for data transfer from refresh worker to persistent FS impl, do not use. */
@ApiStatus.Internal
public interface ChildInfo {
  ChildInfo[] EMPTY_ARRAY = new ChildInfo[0];

  int getId();

  @NotNull CharSequence getName();

  int getNameId();

  String getSymLinkTarget();

  @Nullable("null means children are unknown") ChildInfo[] getChildren();

  FileAttributes getFileAttributes();
}