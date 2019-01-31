/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.psi.javadoc.PsiDocComment;
import org.jetbrains.annotations.Nullable;

/**
 * A valid target element for a Javadoc comment (class member, module declaration etc.)
 */
public interface PsiJavaDocumentedElement extends PsiElement {
  /**
   * Returns the JavaDoc comment for the element, or {@code null} when the element has no JavaDoc comment.
   */
  @Nullable
  PsiDocComment getDocComment();
}