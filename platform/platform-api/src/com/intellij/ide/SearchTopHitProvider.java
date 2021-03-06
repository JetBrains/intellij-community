// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * @author Konstantin Bulenkov
 */
public interface SearchTopHitProvider {
  ExtensionPointName<SearchTopHitProvider> EP_NAME = new ExtensionPointName<>("com.intellij.search.topHitProvider");

  void consumeTopHits(@NotNull String pattern, @NotNull Consumer<Object> collector, @Nullable Project project);

  @NlsSafe
  static String getTopHitAccelerator() {
    return "/";
  }
}
