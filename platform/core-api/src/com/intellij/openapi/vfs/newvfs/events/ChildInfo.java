// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.util.io.FileAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/** An internal class for data transfer from refresh worker to persistent FS impl, do not use. */
@ApiStatus.Internal
public interface ChildInfo {
  ChildInfo[] EMPTY_ARRAY = new ChildInfo[0];
  Comparator<ChildInfo> BY_ID = Comparator.comparing(o->o.getId());

  int getId();

  @NotNull CharSequence getName();

  int getNameId();

  String getSymlinkTarget();

  ChildInfo @Nullable("null means children are unknown") [] getChildren();

  FileAttributes getFileAttributes();

  /**
   * @return flags from {@link #getFileAttributes()} ORed into an int, or -1 if getFileAttributes() is null
   * @see com.intellij.openapi.vfs.newvfs.persistent.PersistentFS.Attributes
   */
  int getFileAttributeFlags();
}