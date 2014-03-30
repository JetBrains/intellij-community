/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public class VirtualFileLogicalRoot extends LogicalRoot {
  private final VirtualFile myVirtualFile;
  private final LogicalRootType myType;

  public VirtualFileLogicalRoot(final VirtualFile virtualFile, final LogicalRootType type) {
    myVirtualFile = virtualFile;
    myType = type;
  }

  public VirtualFileLogicalRoot(final VirtualFile virtualFile) {
    this(virtualFile, LogicalRootType.SOURCE_ROOT);
  }

  @Override
  @Nullable
  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  @Override
  @NotNull
  public LogicalRootType getType() {
    return myType;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VirtualFileLogicalRoot that = (VirtualFileLogicalRoot)o;

    if (!myVirtualFile.equals(that.myVirtualFile)) return false;

    return true;
  }

  public int hashCode() {
    return myVirtualFile.hashCode();
  }
}
