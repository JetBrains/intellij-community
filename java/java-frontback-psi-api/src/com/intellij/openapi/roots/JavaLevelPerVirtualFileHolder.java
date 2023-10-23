// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.WeakHashMap;

public class JavaLevelPerVirtualFileHolder {

  private final @NotNull WeakHashMap<@NotNull VirtualFile, @NotNull LanguageLevel> virtualFile2langLevel = new WeakHashMap<>();

  @Nullable LanguageLevel getLangLevel(@NotNull VirtualFile virtualFile) {
    return virtualFile2langLevel.get(virtualFile);
  }

  void setLangLevel(@NotNull VirtualFile virtualFile, @Nullable LanguageLevel langLevel) {
    if (langLevel == null) {
      virtualFile2langLevel.remove(virtualFile);
    }
    else {
      virtualFile2langLevel.put(virtualFile, langLevel);
    }
  }
}
