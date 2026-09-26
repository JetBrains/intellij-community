// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker;

import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
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

    if (element instanceof PsiClass psiClass) {
      if (DumbService.isDumb(element.getProject())) {
        PsiReferenceList extendList = psiClass.getExtendsList();
        PsiReferenceList implementList = psiClass.getImplementsList();
        List<PsiJavaCodeReferenceElement> referenceElements = new ArrayList<>();
        if (extendList != null) referenceElements.addAll(List.of(extendList.getReferenceElements()));
        if (implementList != null) referenceElements.addAll(List.of(implementList.getReferenceElements()));
        for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
          PsiElement nameElement = referenceElement.getReferenceNameElement();
          if (nameElement != null && nameSeemsDerived(identifier, nameElement.getText())) return;
        }
      } else {
        for (PsiClassType superType : psiClass.getSuperTypes()) {
          if (nameSeemsDerived(identifier, getClassName(superType))) {
            return;
          }
        }
      }
    }

    myIdentifierTokenizer.tokenize(psiIdentifier, consumer);
  }

  private static boolean nameSeemsDerived(String name, @Nullable String source) {
    return source != null && name.toLowerCase(Locale.ROOT).endsWith(source.toLowerCase(Locale.ROOT));
  }

  private static @Nullable String getTypeText(@NotNull PsiElement element) {
    PsiTypeElement typeElement = PsiTreeUtil.getChildOfType(element, PsiTypeElement.class);
    if (DumbService.isDumb(element.getProject())) {
      if (typeElement == null || typeElement.isInferredType()) return null;
      PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
      if (referenceElement == null) return null;
      PsiElement identifier = referenceElement.getReferenceNameElement();
      if (identifier == null) return null;
      return identifier.getText();
    }
    else {
      PsiType type = typeElement != null ? typeElement.getType() : element instanceof PsiVariable ? ((PsiVariable)element).getType() : null;
      return getClassName(type);
    }
  }

  private static @Nullable String getClassName(PsiType type) {
    PsiType component = type == null ? null : type.getDeepComponentType();
    return component instanceof PsiClassType ? ((PsiClassType)component).getClassName() : null;
  }
}


