/*
 * Copyright 2003-2006 Dave Griffith
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
package com.siyeh.ipp.base;

import com.intellij.psi.PsiElement;

public interface PsiElementPredicate {
  /**
   * Check if element satisfies the predicate. All parent elements are supplied here until the satisfying element is found.
   * Use {@link PsiElementContextPredicate} if you need caret position and/or selection.
   */
  boolean satisfiedBy(PsiElement element);
}