// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

public record GetterSetterGenerationOptions(boolean copyAllAnnotations) {
  public static GetterSetterGenerationOptions empty() {
    return new GetterSetterGenerationOptions(false);
  }
}