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

package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Gregory.Shrago
 */
public class PsiDocTagValueManipulator extends AbstractElementManipulator<PsiDocTag> {

  @Override
  public PsiDocTag handleContentChange(@NotNull PsiDocTag tag, @NotNull TextRange range, String newContent) throws IncorrectOperationException {
    final StringBuilder replacement = new StringBuilder( tag.getText() );

    replacement.replace(
      range.getStartOffset(),
      range.getEndOffset(),
      newContent
    );
    return (PsiDocTag)tag.replace(JavaPsiFacade.getInstance(tag.getProject()).getElementFactory().createDocTagFromText(replacement.toString()));
  }

  @NotNull
  @Override
  public TextRange getRangeInElement(@NotNull final PsiDocTag tag) {
    final PsiElement[] elements = tag.getDataElements();
    if (elements.length == 0) {
      final PsiElement name = tag.getNameElement();
      final int offset = name.getStartOffsetInParent() + name.getTextLength();
      return new TextRange(offset, offset);
    }
    final PsiElement first = elements[0];
    final PsiElement last = elements[elements.length - 1];
    return new TextRange(first.getStartOffsetInParent(), last.getStartOffsetInParent()+last.getTextLength());
  }
}