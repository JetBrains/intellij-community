/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public interface ParseResultVisitor<T> {
  T append();
  T error();
  T done(@NotNull IElementType type);
}
