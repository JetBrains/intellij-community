// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;

public interface StubBuilder {
  StubElement buildStubTree(@NotNull PsiFile file);

  /**
   * Return true if {@code node} can't contain stubs, false you can't be sure about that.
   * Implementing this method allows to speed up indexing and some PSI operations (e.g. involving stub-AST switch)
   * by reducing the number of the AST nodes the platform walks to find all stubbed ones.<p></p>
   *
   * Typical implementations usually check {@code node.getElementType()}. They may also check {@code node}'s text.
   * If {@code node} is {@link com.intellij.psi.tree.ILazyParseableElementType lazy-parseable}, retrieving its children via AST
   * would be slow and is not recommended, try to use text (maybe with a lexer) instead.
   *
   * @param parent the parent of {@code node}
   * @param node the AST element to check
   * @see com.intellij.extapi.psi.StubBasedPsiElementBase
   */
  boolean skipChildProcessingWhenBuildingStubs(@NotNull ASTNode parent, @NotNull ASTNode node);
}