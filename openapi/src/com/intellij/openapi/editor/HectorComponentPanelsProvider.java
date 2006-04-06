/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.editor;

import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface HectorComponentPanelsProvider {
  @Nullable UnnamedConfigurable createConfigurable(@NotNull PsiFile file);
}
