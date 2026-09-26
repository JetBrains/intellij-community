// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.ui;

import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.options.UnnamedConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public abstract class ArtifactPropertiesEditor implements UnnamedConfigurable {
  public static final Supplier<@Nls String> VALIDATION_TAB_POINTER =
    JavaCompilerBundle.messagePointer("ArtifactPropertiesEditor.tab.validation");

  public static final Supplier<@Nls String> POST_PROCESSING_TAB_POINTER =
    JavaCompilerBundle.messagePointer("ArtifactPropertiesEditor.tab.post.processing");

  public static final Supplier<@Nls String> PRE_PROCESSING_TAB_POINTER
    = JavaCompilerBundle.messagePointer("ArtifactPropertiesEditor.tab.pre.processing");

  public abstract @Nls String getTabName();

  @Override
  public abstract void apply();

  public @Nullable String getHelpId() {
    return null;
  }
}
