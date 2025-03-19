// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.InternalPersistentJavaLanguageLevelReaderService;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public final class InternalPersistentJavaLanguageLevelReaderServiceImpl implements InternalPersistentJavaLanguageLevelReaderService {
  @Override
  public LanguageLevel getPersistedLanguageLevel(@NotNull VirtualFile fileNotDir) {
    return JavaLanguageLevelPusher.getPushedLanguageLevel(fileNotDir);
  }
}
