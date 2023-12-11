// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.index;

import com.intellij.openapi.roots.impl.JavaLanguageLevelPusher;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.indexing.IndexedFile;
import com.intellij.util.indexing.flavor.FileIndexingFlavorProvider;
import com.intellij.util.indexing.flavor.HashBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaFileIndexingFlavor implements FileIndexingFlavorProvider<LanguageLevel> {
  @Override
  public @Nullable LanguageLevel getFlavor(@NotNull IndexedFile content) {
    return JavaLanguageLevelPusher.getPushedLanguageLevel(content.getFile());
  }

  @Override
  public void buildHash(@NotNull LanguageLevel level, @NotNull HashBuilder hashBuilder) {
    hashBuilder.putInt(level.ordinal());
  }

  @Override
  public int getVersion() {
    return 0;
  }

  @Override
  public @NotNull String getId() {
    return "JavaIndexingFlavorProvider";
  }
}
