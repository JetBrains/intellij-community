/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.source;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.StaticImportResolveProcessor;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class PsiImportStaticReferenceElementImpl extends CompositePsiElement implements PsiImportStaticReferenceElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiImportStaticReferenceElementImpl");
  private volatile String myCanonicalText;

  public PsiImportStaticReferenceElementImpl() {
    super(JavaElementType.IMPORT_STATIC_REFERENCE);
  }

  @Override
  public int getTextOffset() {
    ASTNode refName = findChildByRole(ChildRole.REFERENCE_NAME);
    if (refName != null){
      return refName.getStartOffset();
    }
    else{
      return super.getTextOffset();
    }
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myCanonicalText = null;
  }

  @Override
  public final ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.REFERENCE_NAME:
        return findChildByType(JavaTokenType.IDENTIFIER);

      case ChildRole.QUALIFIER:
        final TreeElement node = getFirstChildNode();
        return node.getElementType() == JavaElementType.JAVA_CODE_REFERENCE ? node : null;

      case ChildRole.DOT:
        return findChildByType(JavaTokenType.DOT);
    }
  }

  @Override
  public final int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaElementType.JAVA_CODE_REFERENCE) {
      return ChildRole.QUALIFIER;
    }
    else if (i == JavaTokenType.DOT) {
      return ChildRole.DOT;
    }
    else if (i == JavaTokenType.IDENTIFIER) {
      return ChildRole.REFERENCE_NAME;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }


  @Override
  public PsiElement getReferenceNameElement() {
    return findChildByRoleAsPsiElement(ChildRole.REFERENCE_NAME);
  }

  @Override
  public PsiReferenceParameterList getParameterList() {
    return null;
  }

  @Override
  @NotNull
  public PsiType[] getTypeParameters() {
    return PsiType.EMPTY_ARRAY;
  }

  @Override
  public PsiElement getQualifier() {
    return findChildByRoleAsPsiElement(ChildRole.QUALIFIER);
  }

  @Override
  public PsiJavaCodeReferenceElement getClassReference() {
    return (PsiJavaCodeReferenceElement)findChildByRoleAsPsiElement(ChildRole.QUALIFIER);
  }

  @Override
  public PsiImportStaticStatement bindToTargetClass(final PsiClass aClass) throws IncorrectOperationException {
    final String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null) throw new IncorrectOperationException();
    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(getProject()).getParserFacade();
    final CompositeElement newRef = (CompositeElement)parserFacade.createReferenceFromText(qualifiedName, null).getNode();
    if (getQualifier() != null) {
      replaceChildInternal(findChildByRole(ChildRole.QUALIFIER), newRef);
      return (PsiImportStaticStatement)getParent();
    }
    else {
      final LeafElement dot = Factory.createSingleLeafElement(JavaTokenType.DOT, ".", 0, 1, SharedImplUtil.findCharTableByTree(newRef), getManager());
      newRef.rawInsertAfterMe(dot);
      final CompositeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("import.statement.identifier.or.asterisk.expected."));
      dot.rawInsertAfterMe(errorElement);
      final CompositeElement parentComposite = (CompositeElement)SourceTreeToPsiMap.psiElementToTree(getParent());
      parentComposite.addInternal(newRef, errorElement, this, Boolean.TRUE);
      parentComposite.deleteChildInternal(this);
      return (PsiImportStaticStatement)SourceTreeToPsiMap.treeElementToPsi(parentComposite);
    }
  }

  @Override
  public boolean isQualified() {
    return findChildByRole(ChildRole.QUALIFIER) != null;
  }

  @Override
  public String getQualifiedName() {
    return getCanonicalText();
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public String getReferenceName() {
    final ASTNode childByRole = findChildByRole(ChildRole.REFERENCE_NAME);
    if (childByRole == null) return "";
    return childByRole.getText();
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return this;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    TreeElement nameChild = (TreeElement)findChildByRole(ChildRole.REFERENCE_NAME);
    if (nameChild == null) return new TextRange(0, getTextLength());
    final int startOffset = nameChild.getStartOffsetInParent();
    return new TextRange(startOffset, startOffset + nameChild.getTextLength());
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    String canonicalText = myCanonicalText;
    if (canonicalText == null) {
      myCanonicalText = canonicalText = calcCanonicalText();
    }
    return canonicalText;
  }

  private String calcCanonicalText() {
    final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)getQualifier();
    if (referenceElement == null) {
      return getReferenceName();
    }
    else {
      return referenceElement.getCanonicalText() + "." + getReferenceName();
    }
  }

  public String toString() {
    return "PsiImportStaticReferenceElement:" + getText();
  }

  @Override
  @NotNull
  public JavaResolveResult advancedResolve(boolean incompleteCode) {
    final JavaResolveResult[] results = multiResolve(incompleteCode);
    if (results.length == 1) return results[0];
    return JavaResolveResult.EMPTY;
  }

  @Override
  @NotNull
  public JavaResolveResult[] multiResolve(boolean incompleteCode) {
    PsiFile file = getContainingFile();
    final ResolveCache resolveCache = ResolveCache.getInstance(file.getProject());
    final ResolveResult[] results = resolveCache.resolveWithCaching(this, OurGenericsResolver.INSTANCE, true, incompleteCode,file);
    return results instanceof JavaResolveResult[] ? (JavaResolveResult[])results : JavaResolveResult.EMPTY_ARRAY;
  }

  private static final class OurGenericsResolver implements ResolveCache.PolyVariantResolver<PsiImportStaticReferenceElementImpl> {
    private static final OurGenericsResolver INSTANCE = new OurGenericsResolver();

    @NotNull
    @Override
    public JavaResolveResult[] resolve(@NotNull final PsiImportStaticReferenceElementImpl referenceElement, final boolean incompleteCode) {
      final PsiElement qualifier = referenceElement.getQualifier();
      if (!(qualifier instanceof PsiJavaCodeReferenceElement)) return JavaResolveResult.EMPTY_ARRAY;
      final PsiElement target = ((PsiJavaCodeReferenceElement)qualifier).resolve();
      if (!(target instanceof PsiClass)) return JavaResolveResult.EMPTY_ARRAY;
      final StaticImportResolveProcessor processor = new StaticImportResolveProcessor(referenceElement);
      target.processDeclarations(processor, ResolveState.initial(), referenceElement, referenceElement);
      return processor.getResults();
    }
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Override
  public PsiElement resolve() {
    return advancedResolve(false).getElement();
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    final String name = getReferenceName();
    if (name == null || !(element instanceof PsiNamedElement) || !name.equals(((PsiNamedElement)element).getName())) {
      return false;
    }

    for (JavaResolveResult result : multiResolve(false)) {
      if (getManager().areElementsEquivalent(result.getElement(), element)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    PsiElement oldIdentifier = findChildByRoleAsPsiElement(ChildRole.REFERENCE_NAME);
    if (oldIdentifier == null) {
      throw new IncorrectOperationException();
    }
    PsiIdentifier identifier = JavaPsiFacade.getInstance(getProject()).getElementFactory().createIdentifier(newElementName);
    oldIdentifier.replace(identifier);
    return this;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PsiMember) ||
        !(element instanceof PsiNamedElement) ||
        ((PsiNamedElement)element).getName() == null) {
      throw new IncorrectOperationException();
    }
    if (!((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC)) {
      if (element instanceof PsiClass && ((PsiClass)element).getContainingClass() == null) {
        // "move inner to upper level" of a statically imported inner class => replace with regular import
        return replaceWithRegularImport((PsiClass) element);
      }
      throw new IncorrectOperationException();
    }

    PsiClass containingClass = ((PsiMember)element).getContainingClass();
    if (containingClass == null) throw new IncorrectOperationException();
    PsiElement qualifier = getQualifier();
    if (qualifier == null) {
      throw new IncorrectOperationException();
    }
    ((PsiReference)qualifier).bindToElement(containingClass);

    PsiElement oldIdentifier = findChildByRoleAsPsiElement(ChildRole.REFERENCE_NAME);
    if (oldIdentifier == null){
      throw new IncorrectOperationException();
    }

    PsiIdentifier identifier = JavaPsiFacade.getInstance(getProject()).getElementFactory().createIdentifier(((PsiNamedElement)element).getName());
    oldIdentifier.replace(identifier);
    return this;
  }

  private PsiElement replaceWithRegularImport(final PsiClass psiClass) throws IncorrectOperationException {
    PsiImportStaticStatement baseStatement = PsiTreeUtil.getParentOfType(getElement(), PsiImportStaticStatement.class);
    PsiImportStatement statement = JavaPsiFacade.getInstance(getProject()).getElementFactory().createImportStatement(psiClass);
    statement = (PsiImportStatement) baseStatement.replace(statement);
    final PsiJavaCodeReferenceElement reference = statement.getImportReference();
    assert reference != null;
    return reference;
  }

  @Override
  public void processVariants(@NotNull PsiScopeProcessor processor) {
    FilterScopeProcessor proc = new FilterScopeProcessor(new ClassFilter(PsiModifierListOwner.class), processor);
    PsiScopesUtil.resolveAndWalk(proc, this, null, true);
  }

  @Override
  @NotNull
  public Object[] getVariants() {
    // IMPLEMENT[dsl]
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitImportStaticReferenceElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
}
