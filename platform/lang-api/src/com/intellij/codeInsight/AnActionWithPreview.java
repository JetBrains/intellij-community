// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * An action that can generate preview. Currently, used in the surrounder popup only
 */
@ApiStatus.Experimental
public interface AnActionWithPreview {
  /**
   * @return preview for an action.
   */
  @NotNull IntentionPreviewInfo getPreview();
}
