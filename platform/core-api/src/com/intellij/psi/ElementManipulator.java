/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 03.04.2003
 * Time: 11:22:05
 *
 * @see com.intellij.psi.ElementManipulators
 */
public interface ElementManipulator<T extends PsiElement> {

  /**
   * Changes the element's text to a new value
   *
   * @param element element to be changed
   * @param range range within the element
   * @param newContent new element text
   * @return changed element
   * @throws IncorrectOperationException if something goes wrong
   */
  T handleContentChange(@NotNull T element, @NotNull TextRange range, String newContent) throws IncorrectOperationException;

  T handleContentChange(@NotNull T element, String newContent) throws IncorrectOperationException;

  @NotNull
  TextRange getRangeInElement(@NotNull T element);
}
