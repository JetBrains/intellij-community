// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
// internal class for data transfer from refresh worker to persistent FS impl, do not use
public class ChildInfo {
  public static final ChildInfo[] EMPTY_ARRAY = new ChildInfo[0];
  public final int id;
  @NotNull
  public final String name;
  public final FileAttributes attributes;
  public final String symLinkTarget;
  @Nullable // null means children are unknown
  public final ChildInfo[] children;

  public ChildInfo(int id, @NotNull String name, FileAttributes attributes, @Nullable ChildInfo[] children, String symLinkTarget) {
    this.id = id;
    this.name = name;
    this.attributes = attributes;
    this.children = children;
    this.symLinkTarget = symLinkTarget;
  }

  @Override
  public String toString() {
    return name +" ("+attributes+")" +(children == null ? "" : "\n  " + StringUtil.join(children, info -> info.toString().replaceAll("\n", "\n  "), "\n  "));
  }
}
