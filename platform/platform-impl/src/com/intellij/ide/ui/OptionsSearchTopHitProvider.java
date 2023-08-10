// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Consumer;

public interface OptionsSearchTopHitProvider {
  @NotNull
  @NonNls
  String getId();

  default boolean preloadNeeded() {
    return true;
  }

  /**
   * Extension point name: com.intellij.search.topHitProvider
   */
  interface ApplicationLevelProvider extends OptionsSearchTopHitProvider, SearchTopHitProvider {
    @NotNull Collection<OptionDescription> getOptions();

    // do not override
    @Override
    default void consumeTopHits(@NotNull String pattern, @NotNull Consumer<Object> collector, @Nullable Project project) {
      OptionsTopHitProvider.consumeTopHits(this, pattern, collector, project);
    }
  }

  /**
   * Extension point name: com.intellij.search.projectOptionsTopHitProvider
   */
  interface ProjectLevelProvider extends OptionsSearchTopHitProvider {
    @NotNull
    Collection<OptionDescription> getOptions(@NotNull Project project);
  }
}
