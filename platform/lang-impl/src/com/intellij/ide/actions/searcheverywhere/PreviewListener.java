// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import kotlin.time.Duration;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface PreviewListener {
  @ApiStatus.Internal
  void onPreviewEditorCreated(SearchEverywhereUI searchEverywhereUI, Editor editor);
  @ApiStatus.Internal
  void onPreviewDataReady(Project project, Object data, Duration duration);
}
