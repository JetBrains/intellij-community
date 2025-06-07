// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

abstract class NullabilityAnnotationPanelModel implements AnnotationPanelModel {
  final NullableNotNullManager myManager;

  protected NullabilityAnnotationPanelModel(NullableNotNullManager manager) {
    myManager = manager;
  }

  @Override
  public boolean hasAdvancedAnnotations() {
    return true;
  }

  static class NullableModel extends NullabilityAnnotationPanelModel {
    NullableModel(NullableNotNullManager manager) {
      super(manager);
    }

    @Override
    public @NotNull String getName() {
      return NullableNotNullDialog.NULLABLE;
    }

    @Override
    public @NotNull String getDefaultAnnotation() {
      return myManager.getDefaultNullable();
    }

    @Override
    public @NotNull List<String> getAnnotations() {
      return myManager.getNullables();
    }

    @Override
    public @NotNull List<String> getAdvancedAnnotations() {
      return myManager.getNullablesWithNickNames();
    }

    @Override
    public @NotNull List<String> getDefaultAnnotations() {
      return myManager.getDefaultNullables();
    }

    @Override
    public @NotNull Set<String> getCheckedAnnotations() {
      return Collections.emptySet();
    }
  }

  static class NotNullModel extends NullabilityAnnotationPanelModel {
    NotNullModel(NullableNotNullManager manager) {
      super(manager);
    }

    @Override
    public @NotNull String getName() {
      return NullableNotNullDialog.NOT_NULL;
    }

    @Override
    public @NotNull String getDefaultAnnotation() {
      return myManager.getDefaultNotNull();
    }

    @Override
    public @NotNull List<String> getAnnotations() {
      return myManager.getNotNulls();
    }

    @Override
    public @NotNull List<String> getAdvancedAnnotations() {
      return myManager.getNotNullsWithNickNames();
    }

    @Override
    public @NotNull List<String> getDefaultAnnotations() {
      return myManager.getDefaultNotNulls();
    }

    @Override
    public @NotNull Set<String> getCheckedAnnotations() {
      return new HashSet<>(myManager.getInstrumentedNotNulls());
    }
  }
}
