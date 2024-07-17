// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.TypeInfo;
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
  private final PsiElement myParent;
  private final String myQualifiedName;

  public ClsPackageReferenceElementImpl(@NotNull PsiElement parent,
                                        @NotNull String canonicalText) {
    myParent = parent;
    myQualifiedName = TypeInfo.internFrequentType(canonicalText);
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  public PsiElement getParent() {
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

  private static class Resolver implements ResolveCache.PolyVariantContextResolver<ClsPackageReferenceElementImpl> {
    public static final Resolver INSTANCE = new Resolver();

    @Override
    public JavaResolveResult @NotNull [] resolve(@NotNull ClsPackageReferenceElementImpl ref,
                                                 @NotNull PsiFile containingFile,
                                                 boolean incompleteCode) {
      final JavaResolveResult resolveResult = ref.advancedResolveImpl(containingFile);
      return resolveResult == null ? JavaResolveResult.EMPTY_ARRAY : new JavaResolveResult[]{resolveResult};
    }
  }

  private JavaResolveResult advancedResolveImpl(@NotNull PsiFile containingFile) {
    PsiElement element = getParent();
    if (element instanceof PsiPackage) return new CandidateInfo(element, PsiSubstitutor.EMPTY);
    PsiElement resolve = JavaPsiFacade.getInstance(containingFile.getProject()).findPackage(myQualifiedName);
    if (resolve == null) return null;
    return new CandidateInfo(resolve, PsiSubstitutor.EMPTY);
  }

  @Override
  public @NotNull JavaResolveResult advancedResolve(boolean incompleteCode) {
    final JavaResolveResult[] results = multiResolve(incompleteCode);
    if (results.length == 1) return results[0];
    return JavaResolveResult.EMPTY;
  }

  @Override
  public JavaResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    PsiFile file = getContainingFile();
    if (file == null) {
      PsiElement root = SyntaxTraverser.psiApi().parents(this).last();
      PsiUtilCore.ensureValid(Objects.requireNonNull(root));
      throw new PsiInvalidElementAccessException(this, "parent=" + myParent + ", root=" + root + ", canonicalText=" + myQualifiedName);
    }
    final ResolveCache resolveCache = ResolveCache.getInstance(file.getProject());
    ResolveResult[] results = resolveCache.resolveWithCaching(this, Resolver.INSTANCE, true, incompleteCode, file);
    if (results.length == 0) return JavaResolveResult.EMPTY_ARRAY;
    return (JavaResolveResult[])results;
  }

  @Override
  public PsiElement resolve() {
    return advancedResolve(false).getElement();
  }

  @Override
  public void processVariants(@NotNull PsiScopeProcessor processor) {
    throw new RuntimeException("Variants are not available for light references");
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
  public String getQualifiedName() {
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
    if (!(element instanceof PsiClass)) return false;
    PsiClass aClass = (PsiClass)element;
    return myQualifiedName.equals(aClass.getQualifiedName()) || getManager().areElementsEquivalent(resolve(), element);
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
    return false;
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