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
package com.intellij.spellchecker;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * @author shkate@jetbrains.com
 */
public class NamedElementTokenizer<T extends PsiNamedElement> extends Tokenizer<T> {
  private final Tokenizer<PsiIdentifier> myIdentifierTokenizer = new PsiIdentifierTokenizer();

  @Override
  public void tokenize(@NotNull T element, TokenConsumer consumer) {
    PsiIdentifier psiIdentifier = PsiTreeUtil.getChildOfType(element, PsiIdentifier.class);
    if (psiIdentifier == null) return;

    String identifier = psiIdentifier.getText();
    if (identifier == null) return;

    if (nameSeemsDerived(identifier, getTypeText(element))) {
      return;
    }

    if (element instanceof PsiClass) {
      for (PsiClassType superType : ((PsiClass)element).getSuperTypes()) {
        if (nameSeemsDerived(identifier, getClassName(superType))) {
          return;
        }
      }
    }

    myIdentifierTokenizer.tokenize(psiIdentifier, consumer);
  }

  private static boolean nameSeemsDerived(String name, @Nullable String source) {
    return source != null && name.toLowerCase(Locale.ROOT).endsWith(source.toLowerCase(Locale.ROOT));
  }

  @Nullable
  private static String getTypeText(PsiElement element) {
    PsiTypeElement typeElement = PsiTreeUtil.getChildOfType(element, PsiTypeElement.class);
    PsiType type = typeElement != null ? typeElement.getType() : element instanceof PsiVariable ? ((PsiVariable)element).getType() : null;
    return getClassName(type);
  }

  @Nullable
  private static String getClassName(PsiType type) {
    PsiType component = type == null ? null : type.getDeepComponentType();
    return component instanceof PsiClassType ? ((PsiClassType)component).getClassName() : null;
  }
}


