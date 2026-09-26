// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.psi;

import org.jetbrains.annotations.Nullable;

public interface RegExpConditional extends RegExpAtom {

  /**
   * Returns condition of this conditional. This can be a numeric group reference, named group reference or lookaround group.
   * @return a RegExpBackRef, RegExpNamedGroupRef or RegExpGroup instance, or null if the expression is incomplete
   */
  @Nullable RegExpAtom getCondition();

  /**
   * Returns the then branch of this conditional.
   * @return a RegExpBranch, or null if the expression is incomplete
   */
  @Nullable RegExpBranch getThenBranch();

  /**
   * Return the else branch of this conditional.
   * @return a RegExpBranch, or null if the expression has no else branch
   */
  @Nullable RegExpBranch getElseBranch();
}
