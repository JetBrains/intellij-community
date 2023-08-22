// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;

/**
 * Filters block related nodes
 */
public final class BlockFilter implements NodeFilter {

  private static final NodeFilter INSTANCE = new BlockFilter();

  private BlockFilter() {}

  @Override
  public boolean accepts(PsiElement element) {
    return element instanceof PsiBlockStatement || element instanceof PsiCodeBlock;
  }

  public static NodeFilter getInstance() {
    return INSTANCE;
  }
}
