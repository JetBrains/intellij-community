// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.Disposable;
import com.intellij.patterns.ElementPattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.function.Supplier;

/**
 * @author yole
 */
interface CompletionProcessEx extends CompletionProcess {
  OffsetsInFile getHostOffsets();
  void registerChildDisposable(@NotNull Supplier<Disposable> child);

  void itemSelected(LookupElement item, char aChar);

  void addWatchedPrefix(int startOffset, ElementPattern<String> restartCondition);

  void addAdvertisement(String message, @Nullable final Color bgColor);

  CompletionParameters getParameters();

  void setParameters(CompletionParameters parameters);
}
