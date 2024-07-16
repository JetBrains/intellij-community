// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  public void tokenize(@NotNull T element, @NotNull TokenConsumer consumer) {
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

  private static @Nullable String getTypeText(PsiElement element) {
    PsiTypeElement typeElement = PsiTreeUtil.getChildOfType(element, PsiTypeElement.class);
    PsiType type = typeElement != null ? typeElement.getType() : element instanceof PsiVariable ? ((PsiVariable)element).getType() : null;
    return getClassName(type);
  }

  private static @Nullable String getClassName(PsiType type) {
    PsiType component = type == null ? null : type.getDeepComponentType();
    return component instanceof PsiClassType ? ((PsiClassType)component).getClassName() : null;
  }
}


