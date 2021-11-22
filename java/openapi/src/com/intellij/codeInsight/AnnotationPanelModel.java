// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public interface AnnotationPanelModel {
  @NlsSafe @NotNull String getName();

  @NlsSafe @NotNull String getDefaultAnnotation();

  /**
   * @return list of immediately available annotations
   */
  @NlsSafe @NotNull List<String> getAnnotations();

  /**
   * @return list of annotations that should be calculated in a slow background action
   */
  @NlsSafe @NotNull List<String> getAdvancedAnnotations();

  /**
   * @return true if there are advanced annotations that should be loaded in background
   */
  default boolean hasAdvancedAnnotations() {
    return false;
  }

  @NlsSafe @NotNull List<String> getDefaultAnnotations();

  @NlsSafe @NotNull Set<String> getCheckedAnnotations();
}
