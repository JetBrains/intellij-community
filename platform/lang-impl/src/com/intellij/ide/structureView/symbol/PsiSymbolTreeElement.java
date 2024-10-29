// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.symbol;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.model.Pointer;
import com.intellij.model.Symbol;
import com.intellij.model.presentation.SymbolDeclarationPresentation;
import com.intellij.model.presentation.SymbolDeclarationPresentationProvider;
import com.intellij.model.psi.PsiSymbolDeclaration;
import com.intellij.model.psi.impl.Declarations;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * @see SymbolDeclarationPresentationProvider
 */
public class PsiSymbolTreeElement extends PsiTreeElementBase<PsiElement> {
  private static final Logger LOG = Logger.getInstance(PsiSymbolTreeElement.class);
  private final @NotNull Pointer<? extends Symbol> mySymbolPointer;

  public PsiSymbolTreeElement(@NotNull PsiSymbolDeclaration declaration) {
    super(declaration.getDeclaringElement());
    mySymbolPointer = declaration.getSymbol().createPointer();
  }

  public final @Nullable PsiSymbolDeclaration getSymbolDeclaration() {
    var declaringElement = getElement();
    if (declaringElement == null) {
      return null;
    }
    var declaredSymbol = getSymbol();
    if (declaredSymbol == null) {
      return null;
    }
    for (PsiSymbolDeclaration symbolDeclaration : Declarations.allDeclarationsInElement(declaringElement)) {
      if (Objects.equals(declaredSymbol, symbolDeclaration.getSymbol())) {
        return symbolDeclaration;
      }
    }
    return null;
  }

  @Override
  public final boolean isValid() {
    return getSymbolDeclaration() != null;
  }

  public final @Nullable Symbol getSymbol() {
    return mySymbolPointer.dereference();
  }

  @Override
  public @NotNull ItemPresentation getPresentation() {
    var symbolDeclaration = getSymbolDeclaration();
    if (symbolDeclaration == null) {
      LOG.debug("No declaration for presentation");
      return this;
    }
    var symbolDeclarationPresentation = SymbolDeclarationPresentation.getFor(symbolDeclaration);
    if (symbolDeclarationPresentation != null) {
      return symbolDeclarationPresentation;
    }

    LOG.debug("No presentation for " + symbolDeclaration + " or declaring element");
    return this;
  }

  @Override
  public @Nullable String getPresentableText() {
    return null;
  }

  @Override
  public boolean canNavigate() {
    var navigatable = getNavigatable();
    return navigatable == null ? super.canNavigate() : navigatable.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    var navigatable = getNavigatable();
    return navigatable == null ? super.canNavigateToSource() : navigatable.canNavigateToSource();
  }

  @Override
  public void navigate(boolean requestFocus) {
    var navigatable = getNavigatable();
    if (navigatable == null) {
      super.navigate(requestFocus);
    }
    else {
      navigatable.navigate(requestFocus);
    }
  }

  protected @Nullable Navigatable getNavigatable() {
    var symbolDeclaration = getSymbolDeclaration();
    return getNavigatable(symbolDeclaration);
  }

  public static @Nullable Navigatable getNavigatable(@Nullable PsiSymbolDeclaration symbolDeclaration) {
    if (symbolDeclaration == null) {
      LOG.debug("No symbol declaration for navigation");
      return null;
    }
    var declaringElement = symbolDeclaration.getDeclaringElement();
    var virtualFile = PsiUtilCore.getVirtualFile(declaringElement);
    if (virtualFile == null) {
      LOG.debug("Unable to find virtual file for: " + symbolDeclaration + "; " + declaringElement);
      return null;
    }

    return PsiNavigationSupport.getInstance().createNavigatable(
      declaringElement.getProject(),
      virtualFile,
      symbolDeclaration.getAbsoluteRange()
        .getStartOffset());
  }

  @Override
  public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
    return Collections.emptyList();
  }

  @Override
  public PsiElement getValue() {
    var declarationElement = super.getValue();
    return declarationElement == null ? null : new DelegatingPsiElementWithSymbolPointer(declarationElement, mySymbolPointer);
  }
}
