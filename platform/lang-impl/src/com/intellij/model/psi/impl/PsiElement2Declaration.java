// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.psi.impl;

import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolDeclaration;
import com.intellij.model.psi.PsiSymbolService;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PsiDeclaredTarget;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PsiElement2Declaration implements PsiSymbolDeclaration {

  private final PsiElement myTargetElement;
  private final PsiElement myDeclaringElement;
  private final TextRange myDeclarationRange;

  PsiElement2Declaration(@NotNull PsiElement targetElement, @NotNull PsiElement declaringElement, @NotNull TextRange range) {
    myTargetElement = targetElement;
    myDeclaringElement = declaringElement;
    myDeclarationRange = range;
  }

  @Override
  public @NotNull Symbol getSymbol() {
    return PsiSymbolService.getInstance().asSymbol(myTargetElement);
  }

  @Override
  public @NotNull PsiElement getDeclaringElement() {
    return myDeclaringElement;
  }

  @Override
  public @NotNull TextRange getRangeInDeclaringElement() {
    return myDeclarationRange;
  }

  /**
   * Adapts target element of unknown origin to a {@code PsiSymbolDeclaration}.
   * E.g. when searching for declarations of a {@link Psi2Symbol PsiElement symbol} we lose info about origin,
   * because the symbol could be obtained from reference or another declaration or any other old code.
   */
  static @Nullable PsiSymbolDeclaration createFromTargetPsiElement(@NotNull PsiElement targetElement) {
    PsiElement identifyingElement = getIdentifyingElement(targetElement);
    if (identifyingElement != null) {
      return new PsiElement2Declaration(targetElement, identifyingElement, rangeOf(identifyingElement));
    }
    return null;
  }

  /**
   * Adapts target element obtained from an element at caret to a {@code PsiSymbolDeclaration}.
   *
   * @param declaredElement  target element (symbol); used for target-based actions, e.g. Find Usages
   * @param declaringElement element at caret from which {@code declaredElement} was obtained; used to determine the declaration range
   */
  static @NotNull PsiSymbolDeclaration createFromDeclaredPsiElement(@NotNull PsiElement declaredElement,
                                                                    @NotNull PsiElement declaringElement) {
    TextRange declarationRange = getDeclarationRangeFromPsi(declaredElement, declaringElement);
    return new PsiElement2Declaration(declaredElement, declaringElement, declarationRange);
  }

  private static @NotNull TextRange getDeclarationRangeFromPsi(@NotNull PsiElement declaredElement, @NotNull PsiElement declaringElement) {
    PsiElement identifyingElement = getIdentifyingElement(declaredElement);
    if (identifyingElement == null) {
      return rangeOf(declaringElement);
    }
    TextRange identifyingRange = relateRange(identifyingElement, rangeOf(identifyingElement), declaringElement);
    if (identifyingRange == null) {
      return rangeOf(declaringElement);
    }
    return identifyingRange;
  }

  static @Nullable PsiSymbolDeclaration createFromPom(@NotNull PomTarget target, @NotNull PsiElement declaringElement) {
    TextRange declarationRange = getDeclarationRangeFromPom(target, declaringElement);
    if (declarationRange == null) {
      return null;
    }
    return new PsiElement2Declaration(
      PomService.convertToPsi(declaringElement.getProject(), target),
      declaringElement,
      declarationRange
    );
  }

  private static @Nullable TextRange getDeclarationRangeFromPom(@NotNull PomTarget target, @NotNull PsiElement declaringElement) {
    if (target instanceof PsiDeclaredTarget declaredTarget) {
      TextRange nameIdentifierRange = declaredTarget.getNameIdentifierRange();
      if (nameIdentifierRange != null) {
        PsiElement navigationElement = declaredTarget.getNavigationElement();
        return relateRange(navigationElement, nameIdentifierRange, declaringElement);
      }
    }
    return rangeOf(declaringElement);
  }

  private static @Nullable PsiElement getIdentifyingElement(@NotNull PsiElement targetElement) {
    if (targetElement instanceof PsiNameIdentifierOwner) {
      return getIdentifyingElement((PsiNameIdentifierOwner)targetElement);
    }
    return null;
  }

  private static @Nullable PsiElement getIdentifyingElement(@NotNull PsiNameIdentifierOwner nameIdentifierOwner) {
    PsiElement identifyingElement = nameIdentifierOwner.getNameIdentifier();
    if (identifyingElement == null) {
      return null;
    }
    TextRange identifyingElementRange = identifyingElement.getTextRange();
    if (identifyingElementRange == null) {
      return null;
    }
    return identifyingElement;
  }

  /**
   * @return range in identifying element relative to range of declaring element,
   * or {@code null} if the elements are from different files
   */
  private static @Nullable TextRange relateRange(@NotNull PsiElement identifyingElement,
                                       @NotNull TextRange rangeInIdentifyingElement,
                                       @NotNull PsiElement declaringElement) {
    if (identifyingElement == declaringElement) {
      return rangeInIdentifyingElement;
    }
    else if (identifyingElement.getContainingFile() == declaringElement.getContainingFile()) {
      TextRange rangeInFile = rangeInIdentifyingElement.shiftRight(identifyingElement.getTextRange().getStartOffset());
      TextRange declaringElementRange = declaringElement.getTextRange();
      if (declaringElementRange.contains(rangeInFile)) {
        return rangeInFile.shiftLeft(declaringElementRange.getStartOffset());
      }
      else {
        return null;
      }
    }
    else {
      return null;
    }
  }

  /**
   * @return range of {@code element} relative to itself
   */
  private static @NotNull TextRange rangeOf(@NotNull PsiElement element) {
    return TextRange.from(0, element.getTextLength());
  }

  @Override
  public String toString() {
    return String.format(
      "PsiElement2Declaration(targetElement=%s, declaringElement=%s, declarationRange=%s)",
      myTargetElement, myDeclaringElement, myDeclarationRange
    );
  }
}
