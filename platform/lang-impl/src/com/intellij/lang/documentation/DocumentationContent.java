// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation;

import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.NonExtendable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;
import java.util.Map;

import static com.intellij.lang.documentation.ClassesKt.imageResolver;

@Experimental
@NonExtendable
public interface DocumentationContent {

  static @NotNull DocumentationContent content(@Nls @NotNull String html) {
    return content(html, Collections.emptyMap());
  }

  /**
   * @param images map from {@code url} of {@code <img src="url">} tag to an image
   */
  static @NotNull DocumentationContent content(
    @Nls @NotNull String html,
    @NotNull Map<@NotNull String, ? extends @NotNull Image> images
  ) {
    return new DocumentationContentData(html, imageResolver(images));
  }
}
