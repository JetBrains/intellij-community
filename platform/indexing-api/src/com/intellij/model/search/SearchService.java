// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Query;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.Predicate;

public interface SearchService {

  static SearchService getInstance() {
    return ServiceManager.getService(SearchService.class);
  }



  @NotNull
  SearchWordParameters.Builder searchWord(@NotNull Project project, @NotNull String word);

  @Contract(value = "_, _ -> new", pure = true)
  @NotNull
  <B, R> Query<? extends R> map(@NotNull Query<? extends B> base, @NotNull Function<? super B, ? extends R> transformation);

  @Contract(value = "_, _ -> new", pure = true)
  @NotNull
  <R> Query<? extends R> filter(@NotNull Query<? extends R> base, @NotNull Predicate<? super R> predicate);
}
