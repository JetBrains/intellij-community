/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.codeInsight.daemon;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This extension point allows highlighting subsystem to define scope (i.e., containing {@link PsiElement})
 * which should be re-highlighted on specified {@link PsiElement} change.
 *
 * For example, {@link com.intellij.codeInsight.daemon.impl.JavaChangeLocalityDetector} specifies that for any change inside code block,
 * only this code block should be re-highlighted (except constructors and class initializers).
 * This optimization could greatly improve highlighting speed.
 */
public interface ChangeLocalityDetector {
  /**
   * @return the psi element (ancestor of the changedElement) which should be re-highlighted/re-inspected, or null if unsure.
   * Examples:
   *  - in Java, when the statement has changed, re-highlight the enclosing code block only.
   *  - in (hypothetical) framework which stores its annotations in comments, e.g. "// @someAnnotation",
   *    when that special comment has changed, re-highlight the whole file.
   *<p/>
   * Note: For performance reasons, please do not traverse PSI tree upwards from here, since this method will be called for the
   *       {@code changedElement} and all its parents anyway.
   *       So only a constant-time check should be enough here, e.g: {@code changedElement.getParent() instanceof PsiCodeBlock}
   *       instead of wrong and slow {@code PsiTreeUtil.findFirstParent(changedElement, PsiCodeBlock.class)}
   */
  @Nullable
  PsiElement getChangeHighlightingDirtyScopeFor(@NotNull PsiElement changedElement);
}