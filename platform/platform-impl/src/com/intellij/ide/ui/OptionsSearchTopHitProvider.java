// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.project.Project;
import kotlin.Unit;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.function.Consumer;

public interface OptionsSearchTopHitProvider {
  @NotNull
  @NonNls
  String getId();

  /**
   * Extension point name: com.intellij.search.topHitProvider
   */
  interface ApplicationLevelProvider extends OptionsSearchTopHitProvider, SearchTopHitProvider {
    @Unmodifiable
    @NotNull Collection<OptionDescription> getOptions();

    // do not override
    @Override
    default void consumeTopHits(@NotNull String pattern, @NotNull Consumer<Object> collector, @Nullable Project project) {
      OptionsTopHitProvider.Companion.consumeTopHits(this, pattern, it -> {
        collector.accept(it);
        return Unit.INSTANCE;
      }, project);
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
