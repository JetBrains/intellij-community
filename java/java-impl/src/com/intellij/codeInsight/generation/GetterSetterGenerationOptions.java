// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import org.jetbrains.annotations.Nullable;

/**
 * @param copyAllAnnotations true to copy all annotations of the field, false to copy only the nullability annotations
 * @param getterTemplate     the name of the getter template, or null for the default template of {@link GetterTemplatesManager}
 * @param setterTemplate     the name of the setter template, or null for the default template of {@link SetterTemplatesManager}
 */
public record GetterSetterGenerationOptions(boolean copyAllAnnotations, @Nullable String getterTemplate, @Nullable String setterTemplate) {
  public GetterSetterGenerationOptions(boolean copyAllAnnotations) {
    this(copyAllAnnotations, null, null);
  }

  public static GetterSetterGenerationOptions empty() {
    return new GetterSetterGenerationOptions(false);
  }
}