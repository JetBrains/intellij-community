// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * author: lesya
 */
public abstract class LibrariesHelper {
  public static LibrariesHelper getInstance(){
    return ApplicationManager.getApplication().getService(LibrariesHelper.class);
  }

  public abstract boolean isClassAvailableInLibrary(final Library library, final @NonNls String fqn);

  public abstract boolean isClassAvailable(@NonNls String[] urls, @NonNls String fqn);

  public abstract @Nullable VirtualFile findJarByClass(final Library library, @NonNls String fqn);

  public abstract @Nullable VirtualFile findRootByClass(@NotNull List<? extends VirtualFile> roots, String fqn);
}
