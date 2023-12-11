// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.java;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface InternalPersistentJavaLanguageLevelReaderService {
  LanguageLevel getPersistedLanguageLevel(@NotNull VirtualFile fileNotDir);

  final class DefaultImpl implements InternalPersistentJavaLanguageLevelReaderService {
    @Override
    public LanguageLevel getPersistedLanguageLevel(@NotNull VirtualFile fileNotDir) {
      return null;
    }
  }
}
