// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Marker interface for retrieving the service which provides a default implementation of ASTFactory.
 */
public interface DefaultASTFactory {
  @NotNull
  LeafElement createComment(@NotNull IElementType type, @NotNull CharSequence text);
}
