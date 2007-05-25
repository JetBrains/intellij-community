/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.meta.PsiMetaBaseOwner;
import com.intellij.psi.meta.PsiMetaDataBase;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.codeStyle.ReferenceAdjuster;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.Set;
import java.util.LinkedHashSet;

/**
 * @author peter
 */
public abstract class AbstractQualifiedReference<T extends AbstractQualifiedReference<T>> extends ASTWrapperPsiElement implements PsiPolyVariantReference, PsiQualifiedReference {
  private static final ResolveCache.PolyVariantResolver<AbstractQualifiedReference> MY_RESOLVER = new ResolveCache.PolyVariantResolver<AbstractQualifiedReference>() {
    public ResolveResult[] resolve(final AbstractQualifiedReference expression, final boolean incompleteCode) {
      return expression.resolveInner();
    }
  };

  public AbstractQualifiedReference(@NotNull final ASTNode node) {
    super(node);
  }

  public final PsiReference getReference() {
    return this;
  }

  public final PsiElement getElement() {
    return this;
  }

  protected abstract ResolveResult[] resolveInner();

  @NotNull
  public final ResolveResult[] multiResolve(final boolean incompleteCode) {
    return getManager().getResolveCache().resolveWithCaching(this, MY_RESOLVER, false, false);
  }

  @Nullable
  public final PsiElement resolve() {
    final ResolveResult[] results = multiResolve(false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  protected boolean processVariantsInner(PsiScopeProcessor processor) {
    final T qualifier = getQualifier();
    if (qualifier == null) {
      return processUnqualifiedVariants(processor);
    }

    final PsiElement psiElement = qualifier.resolve();
    return psiElement == null || psiElement.processDeclarations(processor, PsiSubstitutor.EMPTY, null, this);
  }

  protected boolean processUnqualifiedVariants(final PsiScopeProcessor processor) {
    return PsiScopesUtil.treeWalkUp(processor, this, null);
  }


  public final String getCanonicalText() {
    return getText();
  }

  @SuppressWarnings({"unchecked"})
  @Nullable
  public T getQualifier() {
    return (T)findChildByClass(getClass());
  }

  public final PsiElement handleElementRename(final String newElementName) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    final PsiElement firstChildNode = ObjectUtils.assertNotNull(getFirstChild());
    final PsiElement firstInIdentifier = getClass().isInstance(firstChildNode) ? ObjectUtils.assertNotNull(firstChildNode.getNextSibling()).getNextSibling() : firstChildNode;
    ChangeUtil.removeChildren((CompositeElement)getNode(), (TreeElement)firstInIdentifier, null);
    final PsiElement referenceName = ObjectUtils.assertNotNull(parseReference(newElementName).getReferenceNameElement());
    ChangeUtil.addChild((CompositeElement)getNode(), (TreeElement)referenceName.getNode(), null);
    return this;
  }

  public final PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    if (isReferenceTo(element)) return this;

    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      final String methodName = method.getName();
      if (isDirectlyVisible(method)) return replaceReference(methodName);

      final AbstractQualifiedReference result = replaceReference(method.getContainingClass().getQualifiedName() + "." + methodName);
      final AbstractQualifiedReference qualifier = result.getQualifier();
      assert qualifier != null;
      qualifier.shortenReferences();
      return result;
    }
    if (element instanceof PsiClass) {
      return replaceReference(((PsiClass)element).getQualifiedName()).shortenReferences();
    }
    if (element instanceof PsiPackage) {
      return replaceReference(((PsiPackage)element).getQualifiedName());
    }
    if (element instanceof PsiMetaBaseOwner) {
      final PsiMetaDataBase metaData = ((PsiMetaBaseOwner)element).getMetaData();
      if (metaData != null) {
        final String name = metaData.getName(this);
        if (name != null) {
          return replaceReference(name);
        }
      }
    }
    return this;
  }

  private boolean isDirectlyVisible(final PsiMethod method) {
    final AbstractQualifiedReferenceResolvingProcessor processor = new AbstractQualifiedReferenceResolvingProcessor() {
      protected void process(final PsiElement element) {
        if (getManager().areElementsEquivalent(element, method) && isAccessible(element)) {
          setFound();
        }
      }
    };
    processUnqualifiedVariants(processor);
    return processor.isFound();
  }

  private AbstractQualifiedReference replaceReference(final String newText) {
    final ASTNode newNode = parseReference(newText).getNode();
    getNode().getTreeParent().replaceChild(getNode(), newNode);
    return (AbstractQualifiedReference)newNode.getPsi();
  }

  @NotNull
  protected abstract T parseReference(String newText);

  protected final boolean isAccessible(final PsiElement element) {
    if (element instanceof PsiMember) {
      final PsiMember member = (PsiMember)element;
      return ResolveUtil.isAccessible(member, member.getContainingClass(), member.getModifierList(), this, null, null);
    }
    return true;
  }

  @NotNull
  private AbstractQualifiedReference shortenReferences() {
    final PsiElement refElement = resolve();
    if (refElement instanceof PsiClass) {
      final PsiQualifiedReference reference = ReferenceAdjuster.getClassReferenceToShorten((PsiClass)refElement, false, this);
      if (reference instanceof AbstractQualifiedReference) {
        ((AbstractQualifiedReference)reference).dequalify();
      }
    }
    return this;
  }

  private void dequalify() {
    final AbstractQualifiedReference qualifier = getQualifier();
    if (qualifier != null) {
      getNode().removeChild(qualifier.getNode());
      final PsiElement separator = getSeparator();
      if (separator != null) {
        final ASTNode separatorNode = separator.getNode();
        if (separatorNode != null) {
          getNode().removeChild(separatorNode);
        }
      }
    }
  }

  public boolean isReferenceTo(final PsiElement element) {
    final PsiManager manager = getManager();
    for (final ResolveResult result : multiResolve(false)) {
      if (manager.areElementsEquivalent(element, result.getElement())) return true;
    }
    return false;
  }

  @Nullable
  protected abstract PsiElement getSeparator();

  @Nullable
  protected abstract PsiElement getReferenceNameElement();

  public final TextRange getRangeInElement() {
    final PsiElement element = getSeparator();
    final int length = getTextLength();
    return element == null ? TextRange.from(0, length) : new TextRange(element.getStartOffsetInParent() + element.getTextLength(), length);
  }

  @Nullable
  @NonNls
  public final String getReferenceName() {
    final PsiElement element = getReferenceNameElement();
    return element == null ? null : element.getText();
  }

  public final boolean isSoft() {
    return false;
  }

  protected abstract static class AbstractQualifiedReferenceResolvingProcessor extends BaseScopeProcessor {
    private boolean myFound = false;
    private final Set<ResolveResult> myResults = new LinkedHashSet<ResolveResult>();

    public boolean execute(final PsiElement element, final PsiSubstitutor substitutor) {
      if (isFound()) return false;
      process(element);
      return true;
    }

    protected final void addResult(ResolveResult resolveResult) {
      myResults.add(resolveResult);
    }

    private boolean isFound() {
      return myFound;
    }

    public void handleEvent(final Event event, final Object associated) {
      if (event == Event.SET_CURRENT_FILE_CONTEXT && !myResults.isEmpty()) {
        setFound();
      }
      super.handleEvent(event, associated);
    }

    protected final void setFound() {
      myFound = true;
    }

    protected abstract void process(PsiElement element);

    public Set<ResolveResult> getResults() {
      return myResults;
    }
  }

}
