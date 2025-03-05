// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl.livePreview;

import com.intellij.find.FindModel;
import com.intellij.find.impl.livePreview.SearchResults.SearchArea;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * An interface to provide custom {@link SearchArea} for {@link com.intellij.find.EditorSearchSession}
 * All non-null search areas will be aggregated across defined providers, otherwise the global search area (whole editor) will be used.
 *
 * @see SearchResults#globalSearchArea
 * @see FindModel#isGlobal()
 */
@ApiStatus.Experimental
public interface EditorSearchAreaProvider {
  ExtensionPointName<EditorSearchAreaProvider> EP_NAME = ExtensionPointName.create("com.intellij.editorSearchAreaProvider");

  @RequiresEdt
  static @Unmodifiable List<EditorSearchAreaProvider> getEnabled(@NotNull Editor editor, @NotNull FindModel findModel) {
    return ContainerUtil.filter(EP_NAME.getExtensionList(), p -> p.isEnabled(editor, findModel));
  }

  @RequiresEdt
  boolean isEnabled(@NotNull Editor editor, @NotNull FindModel findModel);

  @RequiresEdt
  @Nullable
  SearchArea getSearchArea(@NotNull Editor editor, @NotNull FindModel findModel);
}
