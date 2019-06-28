// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeHighlighting;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Extension {@code com.intellij.highlightingPassFactory} to register {@link TextEditorHighlightingPassFactory}.
 */
public interface TextEditorHighlightingPassFactoryRegistrar {
  void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project);
}
