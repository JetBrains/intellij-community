// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.impl.CachedIntentions;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

//will be deleted
@ApiStatus.Experimental
@ApiStatus.Internal
public class CommandCompletionServiceApi {
  public void cacheActions(@NotNull Editor editor, @NotNull PsiFile file, @NotNull CachedIntentions intentions){

  }
}
