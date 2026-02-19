// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render;

import org.jetbrains.annotations.NotNull;

final class DocRendererMemoryManager extends AbstractDocRenderMemoryManager<DocRenderer> {
  DocRendererMemoryManager() {
    super("doc.render.cache.size");
  }

  @Override
  void destroy(@NotNull DocRenderer renderer) {
    renderer.clearCachedComponent();
  }
}
