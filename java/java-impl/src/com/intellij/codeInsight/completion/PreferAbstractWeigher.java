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

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PreferAbstractWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement item, @NotNull final CompletionLocation location) {
    if (location.getCompletionType() != CompletionType.SMART) {
      return null;
    }

    final PsiElement position = location.getCompletionParameters().getPosition();
    final PsiElement parent = position.getParent();
    if (!(parent instanceof PsiJavaCodeReferenceElement) ||
        !(parent.getParent() instanceof PsiTypeElement) ||
        !(parent.getParent().getParent() instanceof PsiInstanceOfExpression)) {
      return null;
    }

    final Object object = item.getObject();
    if (object instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)object;
      if (aClass.isInterface()) {
        return 2;
      }
      if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return 1;
      }
    }

    return 0;
  }
}
