// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ClsPackageReferenceElementImpl extends ClsElementImpl implements PsiJavaCodeReferenceElement, PsiQualifiedReferenceElement {
  @NotNull
  private final PsiElement myParent;
  @NotNull
  private final String myQualifiedName;

  public ClsPackageReferenceElementImpl(@NotNull PsiElement parent,
                                        @NotNull String canonicalText) {
    myParent = parent;
    myQualifiedName = canonicalText;
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  public @NotNull PsiElement getParent() {
    return myParent;
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Override
  public @NotNull String getCanonicalText() {
    return myQualifiedName;
  }

  private static class Resolver implements ResolveCache.AbstractResolver<ClsPackageReferenceElementImpl, JavaResolveResult> {
    public static final Resolver INSTANCE = new Resolver();

    @Override
    public JavaResolveResult resolve(@NotNull ClsPackageReferenceElementImpl ref, boolean incompleteCode) {
      return ref.advancedResolveImpl(ref.getContainingFile());
    }
  }

  private JavaResolveResult advancedResolveImpl(@NotNull PsiFile containingFile) {
    PsiElement resolve = JavaPsiFacade.getInstance(containingFile.getProject()).findPackage(myQualifiedName);
    if (resolve == null) return null;
    return new CandidateInfo(resolve, PsiSubstitutor.EMPTY);
  }

  @Override
  public @NotNull JavaResolveResult advancedResolve(boolean incompleteCode) {
    JavaResolveResult result = ResolveCache.getInstance(getProject())
      .resolveWithCaching(this, Resolver.INSTANCE, false, incompleteCode);
    return result == null ? JavaResolveResult.EMPTY : result;
  }

  @Override
  public JavaResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    PsiFile file = getContainingFile();
    if (file == null) {
      PsiElement root = SyntaxTraverser.psiApi().parents(this).last();
      PsiUtilCore.ensureValid(Objects.requireNonNull(root));
      throw new PsiInvalidElementAccessException(this, "parent=" + myParent + ", root=" + root + ", canonicalText=" + myQualifiedName);
    }
    JavaResolveResult result = advancedResolve(incompleteCode);
    return result == JavaResolveResult.EMPTY ? JavaResolveResult.EMPTY_ARRAY : new JavaResolveResult[]{result};
  }

  @Override
  public PsiElement resolve() {
    return advancedResolve(true).getElement();
  }

  @Override
  public void processVariants(@NotNull PsiScopeProcessor processor) {
    throw new RuntimeException("Variants are not available for compiled references");
  }

  @Override
  public PsiElement getReferenceNameElement() {
    return null;
  }

  @Override
  public PsiReferenceParameterList getParameterList() {
    return null;
  }

  @Override
  public @NotNull String getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  public String getReferenceName() {
    return myQualifiedName;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    if (!(element instanceof PsiPackage)) return false;
    PsiPackage aPackage = (PsiPackage)element;
    return myQualifiedName.equals(aPackage.getQualifiedName()) || getManager().areElementsEquivalent(resolve(), element);
  }

  @Override
  public Object @NotNull [] getVariants() {
    throw new RuntimeException("Variants are not available for references to compiled code");
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public void appendMirrorText(final int indentLevel, final @NotNull StringBuilder buffer) {
    buffer.append(getCanonicalText());
  }

  @Override
  protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.JAVA_CODE_REFERENCE);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  @Override
  public @NotNull PsiElement getElement() {
    return this;
  }

  @Override
  public PsiType @NotNull [] getTypeParameters() {
    return PsiType.EMPTY_ARRAY;
  }

  @Override
  public boolean isQualified() {
    return true;
  }

  @Override
  public PsiElement getQualifier() {
    return null;
  }

  @Override
  public String getText() {
    return myQualifiedName;
  }

  @Override
  public String toString() {
    return "ClsPackageReferenceElement:" + getText();
  }
}