// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Please use PsiCommentImpl directly
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
public class PsiCoreCommentImpl extends PsiCommentImpl {
  public PsiCoreCommentImpl(@NotNull IElementType type, CharSequence text) {
    super(type, text);
  }
}
