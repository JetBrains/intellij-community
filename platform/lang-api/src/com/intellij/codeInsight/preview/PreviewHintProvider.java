// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.preview;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Provides the possibility to display a preview component when holding the Shift key and hovering over a PSI element in the text editor.
 * These preview components are currently used primarily for displaying color values.
 *
 * @deprecated This API will be removed together with `ide.shift.hover.image.preview` Registry key.
 * Implement gutter icons and/or show the image in the documentation instead.
 */
@Deprecated
public interface PreviewHintProvider {
  ExtensionPointName<PreviewHintProvider> EP_NAME = ExtensionPointName.create("com.intellij.previewHintProvider");

  /**
   * Returns true if Shift-hover preview is supported for the given file.
   *
   * @param file the file to check for preview availability
   * @return true if preview is supported, false otherwise.
   */
  boolean isSupportedFile(PsiFile file);

  /**
   * Returns the Swing component to be displayed in the Shift-preview popup for the specified element.
   *
   * @param element the element for which preview is requested
   * @return the component or null if no preview is available for the specified element.
   */
  @Nullable
  JComponent getPreviewComponent(@NotNull PsiElement element);
}
