// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * @author Konstantin Bulenkov
 */
public interface SearchTopHitProvider {
  ExtensionPointName<SearchTopHitProvider> EP_NAME = ExtensionPointName.create("com.intellij.search.topHitProvider");

  void consumeTopHits(@NotNull String pattern, @NotNull Consumer<Object> collector, @Nullable Project project);

  static String getTopHitAccelerator() {
    return Registry.is("new.search.everywhere") ? "/" : "#";
  }
}
