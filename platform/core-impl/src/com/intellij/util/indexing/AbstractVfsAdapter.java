/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util.indexing;


import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;

public abstract class AbstractVfsAdapter {
  protected static final boolean ourUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();

  private static class AbstractVfsAdapterHolder {
    private static final AbstractVfsAdapter ourInstance = ApplicationManager.getApplication().getComponent(AbstractVfsAdapter.class);
  }

  public static AbstractVfsAdapter getInstance() {
    return AbstractVfsAdapterHolder.ourInstance;
  }

  @Nullable
  public abstract VirtualFile findFileById(final int id);

  @Nullable
  public abstract VirtualFile findFileByIdIfCached(final int id);

  public abstract boolean wereChildrenAccessed(VirtualFile file);

  public abstract Iterable<VirtualFile> getChildren(VirtualFile file);

  public abstract boolean getFlag(VirtualFile file, int flag);

  public abstract void setFlag(VirtualFile file, int flag, boolean value);

  public abstract void iterateCachedFilesRecursively(VirtualFile root, VirtualFileVisitor visitor);

  public abstract boolean isMock(VirtualFile file);

  @Nullable
  public abstract IndexableFileSet getAdditionalIndexableFileSet();

  public abstract DataInputStream readTimeStampAttribute(VirtualFile key);

  public abstract DataOutputStream writeTimeStampAttribute(VirtualFile key);
}
