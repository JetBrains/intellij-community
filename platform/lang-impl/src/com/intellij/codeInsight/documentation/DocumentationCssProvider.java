// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface DocumentationCssProvider {

  ExtensionPointName<DocumentationCssProvider>
    EP_NAME = ExtensionPointName.create("com.intellij.documentationCssProvider");

  /**
   * Allows adding custom CSS to the rendered documentation.
   *
   * @param scaleFunction use to scale pixel values in CSS, e.g.: {@code "margin: " + scale(10) + "px" }
   * @param isInlineEditorContext true if the documentation is shown in an inline editor context
   */
  String generateCss(@NotNull Function<@NotNull Integer, @NotNull Integer> scaleFunction, boolean isInlineEditorContext);
}
