/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.editor;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface HectorComponentPanelsProvider extends ProjectComponent {
  @Nullable HectorComponentPanel createConfigurable(@NotNull PsiFile file);
}
