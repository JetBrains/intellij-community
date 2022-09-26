// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

@ApiStatus.Internal
public interface DocRenderDataProvider {
  static DocRenderDataProvider getInstance() {
    return ApplicationManager.getApplication().getService(DocRenderDataProvider.class);
  }

  @Nullable
  default DocRenderData getDataAroundOffset(@NotNull Editor editor, int offset) {
    return null;
  }

  Collection<? extends DocRenderData> getItems(@NotNull Editor editor);
}
