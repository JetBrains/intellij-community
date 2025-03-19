// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.symbol;

import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.model.Pointer;
import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolDeclaration;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.Collection;
import java.util.Objects;

/**
 * This class is the hacky way to solve the problem of file structure view.
 * Our file structure view expects nodes to be created for psi elements, and it uses {@link PsiTreeElementBase#getValue()} as equality
 * objects in the internal caching mechanisms. However, symbol declarations structure view may have multiple declarations per psi element,
 * therefore we need to distinct them.
 * This class delegating everything to the declaration element, but also includes the symbol for hashCode/equality computations.
 *
 * @see StructureViewComponent.MyNodeWrapper#equals(Object)
 * @see StructureViewComponent#unwrapElement(Object)
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public final class DelegatingPsiElementWithSymbolPointer implements PsiElement, SyntheticElement {
  private final @NotNull PsiElement myDeclarationElement;
  private final @NotNull Pointer<? extends Symbol> mySymbolPointer;

  DelegatingPsiElementWithSymbolPointer(@NotNull PsiElement declarationElement, @NotNull Pointer<? extends Symbol> symbolPointer) {
    myDeclarationElement = declarationElement;
    mySymbolPointer = symbolPointer;
  }

  public @NotNull PsiElement getDelegate() {
    return myDeclarationElement;
  }

  @Override
  @Contract(pure = true)
  public @NotNull Project getProject() throws PsiInvalidElementAccessException {
    return myDeclarationElement.getProject();
  }

  @Override
  @Contract(pure = true)
  public @NotNull Language getLanguage() {
    return myDeclarationElement.getLanguage();
  }

  @Override
  @Contract(pure = true)
  public PsiManager getManager() {
    return myDeclarationElement.getManager();
  }

  @Override
  @Contract(pure = true)
  public PsiElement @NotNull [] getChildren() {
    return myDeclarationElement.getChildren();
  }

  @Override
  @Contract(pure = true)
  public PsiElement getParent() {
    return myDeclarationElement.getParent();
  }

  @Override
  @Contract(pure = true)
  public PsiElement getFirstChild() {
    return myDeclarationElement.getFirstChild();
  }

  @Override
  @Contract(pure = true)
  public PsiElement getLastChild() {
    return myDeclarationElement.getLastChild();
  }

  @Override
  @Contract(pure = true)
  public PsiElement getNextSibling() {
    return myDeclarationElement.getNextSibling();
  }

  @Override
  @Contract(pure = true)
  public PsiElement getPrevSibling() {
    return myDeclarationElement.getPrevSibling();
  }

  @Override
  @Contract(pure = true)
  public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
    return myDeclarationElement.getContainingFile();
  }

  @Override
  @Contract(pure = true)
  public TextRange getTextRange() {
    return myDeclarationElement.getTextRange();
  }

  @Override
  @Contract(pure = true)
  public @NotNull TextRange getTextRangeInParent() {
    return myDeclarationElement.getTextRangeInParent();
  }

  @Override
  @Contract(pure = true)
  public int getStartOffsetInParent() {
    return myDeclarationElement.getStartOffsetInParent();
  }

  @Override
  @Contract(pure = true)
  public int getTextLength() {
    return myDeclarationElement.getTextLength();
  }

  @Override
  @Contract(pure = true)
  public @Nullable PsiElement findElementAt(int offset) {
    return myDeclarationElement.findElementAt(offset);
  }

  @Override
  @Contract(pure = true)
  public @Nullable PsiReference findReferenceAt(int offset) {
    return myDeclarationElement.findReferenceAt(offset);
  }

  @Override
  @Contract(pure = true)
  public int getTextOffset() {
    return myDeclarationElement.getTextOffset();
  }

  @Override
  @Contract(pure = true)
  public @NlsSafe String getText() {
    return myDeclarationElement.getText();
  }

  @Override
  @Contract(pure = true)
  public char @NotNull [] textToCharArray() {
    return myDeclarationElement.textToCharArray();
  }

  @Override
  @Contract(pure = true)
  public PsiElement getNavigationElement() {
    return myDeclarationElement.getNavigationElement();
  }

  @Override
  @Contract(pure = true)
  public PsiElement getOriginalElement() {
    return this;
  }

  @Override
  @Contract(pure = true)
  public boolean textMatches(@NotNull CharSequence text) {
    return myDeclarationElement.textMatches(text);
  }

  @Override
  @Contract(pure = true)
  public boolean textMatches(@NotNull PsiElement element) {
    return myDeclarationElement.textMatches(element);
  }

  @Override
  @Contract(pure = true)
  public boolean textContains(char c) {
    return myDeclarationElement.textContains(c);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    myDeclarationElement.accept(visitor);
  }

  @Override
  public void acceptChildren(@NotNull PsiElementVisitor visitor) {
    myDeclarationElement.acceptChildren(visitor);
  }

  @Override
  public PsiElement copy() {
    return myDeclarationElement.copy();
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    return myDeclarationElement.add(element);
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, @Nullable PsiElement anchor) throws IncorrectOperationException {
    return myDeclarationElement.addBefore(element, anchor);
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, @Nullable PsiElement anchor) throws IncorrectOperationException {
    return myDeclarationElement.addAfter(element, anchor);
  }

  @Override
  @Deprecated
  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    myDeclarationElement.checkAdd(element);
  }

  @Override
  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    return myDeclarationElement.addRange(first, last);
  }

  @Override
  public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    return myDeclarationElement.addRangeBefore(first, last, anchor);
  }

  @Override
  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException {
    return myDeclarationElement.addRangeAfter(first, last, anchor);
  }

  @Override
  public void delete() throws IncorrectOperationException {
    myDeclarationElement.delete();
  }

  @Override
  @Deprecated
  public void checkDelete() throws IncorrectOperationException {
    myDeclarationElement.checkDelete();
  }

  @Override
  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    myDeclarationElement.deleteChildRange(first, last);
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    return myDeclarationElement.replace(newElement);
  }

  @Override
  @Contract(pure = true)
  public boolean isValid() {
    return myDeclarationElement.isValid() && mySymbolPointer.dereference() != null;
  }

  @Override
  @Contract(pure = true)
  public boolean isWritable() {
    return myDeclarationElement.isWritable();
  }

  @Override
  @ApiStatus.Experimental
  public @NotNull Collection<? extends @NotNull PsiSymbolDeclaration> getOwnDeclarations() {
    return myDeclarationElement.getOwnDeclarations();
  }

  @Override
  @ApiStatus.Experimental
  public @NotNull Collection<? extends @NotNull PsiSymbolReference> getOwnReferences() {
    return myDeclarationElement.getOwnReferences();
  }

  @Override
  @Contract(pure = true)
  public @Nullable PsiReference getReference() {
    return myDeclarationElement.getReference();
  }

  @Override
  @Contract(pure = true)
  public PsiReference @NotNull [] getReferences() {
    return myDeclarationElement.getReferences();
  }

  @Override
  @Contract(pure = true)
  public <T> @Nullable T getCopyableUserData(@NotNull Key<T> key) {
    return myDeclarationElement.getCopyableUserData(key);
  }

  @Override
  public <T> void putCopyableUserData(@NotNull Key<T> key, @Nullable T value) {
    myDeclarationElement.putCopyableUserData(key, value);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return myDeclarationElement.processDeclarations(processor, state, lastParent, place);
  }

  @Override
  @Contract(pure = true)
  public @Nullable PsiElement getContext() {
    return myDeclarationElement.getContext();
  }

  @Override
  @Contract(pure = true)
  public boolean isPhysical() {
    return myDeclarationElement.isPhysical();
  }

  @Override
  @Contract(pure = true)
  public @NotNull GlobalSearchScope getResolveScope() {
    return myDeclarationElement.getResolveScope();
  }

  @Override
  @Contract(pure = true)
  public @NotNull SearchScope getUseScope() {
    return myDeclarationElement.getUseScope();
  }

  @Override
  @Contract(pure = true)
  public ASTNode getNode() {
    return myDeclarationElement.getNode();
  }

  @Override
  @Contract(pure = true)
  public @NonNls String toString() {
    return myDeclarationElement.toString();
  }

  @Override
  @Contract(pure = true)
  public boolean isEquivalentTo(PsiElement another) {
    return myDeclarationElement.isEquivalentTo(another);
  }

  @Override
  public Icon getIcon(int flags) {
    return myDeclarationElement.getIcon(flags);
  }

  @Override
  public <T> @Nullable T getUserData(@NotNull Key<T> key) {
    return myDeclarationElement.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myDeclarationElement.putUserData(key, value);
  }
  
  public @NotNull Pointer<? extends Symbol> getSymbolPointer() {
    return mySymbolPointer;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DelegatingPsiElementWithSymbolPointer pointer = (DelegatingPsiElementWithSymbolPointer)o;

    if (!myDeclarationElement.equals(pointer.myDeclarationElement)) {
      return false;
    }

    return Objects.equals(mySymbolPointer.dereference(), pointer.mySymbolPointer.dereference());
  }

  @Override
  public int hashCode() {
    return Objects.hash(myDeclarationElement, mySymbolPointer.dereference());
  }
}
