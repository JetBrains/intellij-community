// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation;

import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface DocumentationImageResolver {

  /**
   * Resolves an {@code Image} by a URL from a {@code src} attribute value of an {@code img} tag.
   * Ideal implementation gets an image from a pre-computed map using {@code url} as a key.
   *
   * @return resolved image, or {@code null} if no image can be found by this {@code url}
   */
  @RequiresEdt
  @Nullable Image resolveImage(@NotNull String url);
}
