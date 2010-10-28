/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class UnfocusedNameIdentifier extends CompletionConfidence {
  @Override
  public Boolean shouldFocusLookup(@NotNull CompletionParameters parameters) {
    final PsiElement position = parameters.getPosition();
    final PsiElement parent = position.getParent();
    if (parent instanceof PsiNameIdentifierOwner) {
      final PsiElement nameIdentifier = ((PsiNameIdentifierOwner)parent).getNameIdentifier();
      if (nameIdentifier == position) {
        return false;
      }

      if (nameIdentifier != null && position.getTextRange().equals(nameIdentifier.getTextRange())) {
        //sometimes name identifiers are non-physical (e.g. Groovy)
        return false;
      }
    }
    return null;
  }
}
