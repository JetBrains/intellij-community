// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.ui;

import org.jetbrains.annotations.NotNull;

public abstract class FacetEditorValidator {
  public abstract @NotNull ValidationResult check();
}
