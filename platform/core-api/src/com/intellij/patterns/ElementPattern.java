// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.patterns;

import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.Nullable;

/**
 * A way to describe conditions on objects in a declarative way. It's frequently used to specify PSI element location,
 * e.g. for {@link com.intellij.psi.PsiReferenceContributor} or {@link com.intellij.codeInsight.completion.CompletionContributor}.
 * A typical pattern might look like {@code psiElement().afterLeaf("@").withParent(psiReferenceExpression().referencing(someTargetPattern))}.
 * Please don't abuse patterns: when they get long, it becomes hard to understand and debug what goes wrong,
 * which pattern doesn't match and why.
 * For pattern creation, see {@link StandardPatterns}, {@link PlatformPatterns} and their inheritors.
 * <p>
 * Please see the <a href="https://plugins.jetbrains.com/docs/intellij/element-patterns.html">IntelliJ Platform Docs</a>
 * for a high-level overview.
 */
public interface ElementPattern<T> {

  boolean accepts(@Nullable Object o);

  boolean accepts(@Nullable Object o, final ProcessingContext context);

  ElementPatternCondition<T> getCondition();
}
