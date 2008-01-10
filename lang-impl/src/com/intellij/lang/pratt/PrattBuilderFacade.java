/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;

/**
 * @author peter
 */
public interface PrattBuilderFacade {
  PrattBuilderFacade expecting(@Nullable String expectedMessage);

  PrattBuilderFacade withLowestPriority(int priority);

  @Nullable
  IElementType parse();

  @NotNull
  LinkedList<IElementType> getResultTypes();

}