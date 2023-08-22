// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class LibraryDetectionManager {
  public static LibraryDetectionManager getInstance() {
    return ApplicationManager.getApplication().getService(LibraryDetectionManager.class);
  }

  public abstract boolean processProperties(@NotNull List<? extends VirtualFile> files, @NotNull LibraryPropertiesProcessor processor);

  @Nullable
  public abstract Pair<LibraryType<?>, LibraryProperties<?>> detectType(@NotNull List<? extends VirtualFile> files);

  public interface LibraryPropertiesProcessor {
    <P extends LibraryProperties> boolean processProperties(@NotNull LibraryKind kind, @NotNull P properties);
  }
}
