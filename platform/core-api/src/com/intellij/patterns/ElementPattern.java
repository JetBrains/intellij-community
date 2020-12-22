/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 *
 * @author peter
 */
public interface ElementPattern<T> {

  boolean accepts(@Nullable Object o);

  boolean accepts(@Nullable Object o, final ProcessingContext context);

  ElementPatternCondition<T> getCondition();
}
