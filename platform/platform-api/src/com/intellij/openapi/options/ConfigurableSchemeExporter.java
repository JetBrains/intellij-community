// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.OutputStream;

/**
 * Allows to configure export prior to export itself.
 *
 * @param <C>
 */
public abstract class ConfigurableSchemeExporter<C,T extends Scheme> extends SchemeExporter<T> {
  @Override
  public final void exportScheme(@Nullable Project project, @NotNull T scheme, @NotNull OutputStream outputStream) throws Exception {
    exportScheme(scheme, outputStream, null);
  }

  public abstract void exportScheme(@NotNull T scheme, @NotNull OutputStream outputStream, @Nullable C exportConfig) throws Exception;

  public abstract @Nullable C getConfiguration(@NotNull Component parent, @NotNull T scheme);
}
