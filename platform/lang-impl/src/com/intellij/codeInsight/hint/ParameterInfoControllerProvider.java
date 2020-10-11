// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint;

import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ParameterInfoControllerProvider {
  ExtensionPointName<ParameterInfoControllerProvider> EP_NAME =
    new ExtensionPointName<>("com.intellij.codeInsight.parameterInfo.controller.provider");

  @Nullable ParameterInfoControllerBase create(@NotNull Project project,
                                               @NotNull Editor editor,
                                               int lbraceOffset,
                                               Object[] descriptors,
                                               Object highlighted,
                                               PsiElement parameterOwner,
                                               @NotNull ParameterInfoHandler<? extends PsiElement, ?> handler,
                                               boolean showHint,
                                               boolean requestFocus);
}
