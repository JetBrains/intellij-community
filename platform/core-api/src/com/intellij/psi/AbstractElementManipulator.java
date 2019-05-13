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
 * @author Dmitry Avdeev
 */
public abstract class AbstractElementManipulator<T extends PsiElement> implements ElementManipulator<T> {

  @Override
  public T handleContentChange(@NotNull final T element, final String newContent) throws IncorrectOperationException {
    return handleContentChange(element, getRangeInElement(element), newContent);
  }

  @Override
  @NotNull
  public TextRange getRangeInElement(@NotNull final T element) {
    return new TextRange(0, element.getTextLength());
  }
}
