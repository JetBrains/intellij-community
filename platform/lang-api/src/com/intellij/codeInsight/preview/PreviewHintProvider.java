/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 * @author yole
 */
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
