// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.source.codeStyle.JavaReferenceAdjuster;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author peter
 */
public abstract class AbstractQualifiedReference<T extends AbstractQualifiedReference<T>> extends ASTWrapperPsiElement
  implements PsiPolyVariantReference, PsiQualifiedReferenceElement {
  private static final ResolveCache.PolyVariantResolver<AbstractQualifiedReference> MY_RESOLVER = new ResolveCache.PolyVariantResolver<AbstractQualifiedReference>() {
    @NotNull
    @Override
    public ResolveResult[] resolve(@NotNull final AbstractQualifiedReference expression, final boolean incompleteCode) {
      return expression.resolveInner();
    }
  };

  protected AbstractQualifiedReference(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public final PsiReference getReference() {
    return this;
  }

  @Override
  public final PsiElement getElement() {
    return this;
  }

  @NotNull
  protected abstract ResolveResult[] resolveInner();

  @Override
  @NotNull
  public final ResolveResult[] multiResolve(final boolean incompleteCode) {
    PsiFile file = getContainingFile();
    return ResolveCache.getInstance(file.getProject()).resolveWithCaching(this, MY_RESOLVER, true, false,file);
  }

  @Override
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
    return psiElement == null || psiElement.processDeclarations(processor, ResolveState.initial(), null, this);
  }

  protected boolean processUnqualifiedVariants(final PsiScopeProcessor processor) {
    return PsiScopesUtil.treeWalkUp(processor, this, null);
  }


  @Override
  @NotNull
  public  String getCanonicalText() {
    return getText();
  }

  @Override
  @SuppressWarnings({"unchecked"})
  @Nullable
  public T getQualifier() {
    return (T)findChildByClass(getClass());
  }

  @Override
  public PsiElement handleElementRename(final String newElementName) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    final PsiElement firstChildNode = ObjectUtils.assertNotNull(getFirstChild());
    final PsiElement firstInIdentifier = getClass().isInstance(firstChildNode) ? ObjectUtils.assertNotNull(firstChildNode.getNextSibling()).getNextSibling() : firstChildNode;
    getNode().removeRange(firstInIdentifier.getNode(), null);
    final PsiElement referenceName = ObjectUtils.assertNotNull(parseReference(newElementName).getReferenceNameElement());
    getNode().addChild(referenceName.getNode());
    return this;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
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
    if (element instanceof PsiMetaOwner) {
      final PsiMetaData metaData = ((PsiMetaOwner)element).getMetaData();
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
      @Override
      protected void process(final PsiElement element) {
        if (getManager().areElementsEquivalent(element, method) && isAccessible(element)) {
          setFound();
        }
      }
    };
    processUnqualifiedVariants(processor);
    return processor.isFound();
  }

  protected final AbstractQualifiedReference replaceReference(final String newText) {
    final ASTNode newNode = parseReference(newText).getNode();
    getNode().getTreeParent().replaceChild(getNode(), newNode);
    return (AbstractQualifiedReference)newNode.getPsi();
  }

  @NotNull
  protected abstract T parseReference(String newText);

  protected boolean isAccessible(final PsiElement element) {
    if (element instanceof PsiMember) {
      final PsiMember member = (PsiMember)element;
      return JavaResolveUtil.isAccessible(member, member.getContainingClass(), member.getModifierList(), this, null, null);
    }
    return true;
  }

  @NotNull
  protected AbstractQualifiedReference shortenReferences() {
    final PsiElement refElement = resolve();
    if (refElement instanceof PsiClass) {
      final PsiQualifiedReference reference = JavaReferenceAdjuster.getClassReferenceToShorten((PsiClass)refElement, false, this);
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

  @Override
  public boolean isReferenceTo(final PsiElement element) {
    final PsiManager manager = getManager();
    for (final ResolveResult result : multiResolve(false)) {
      if (manager.areElementsEquivalent(result.getElement(), element)) return true;
    }
    return false;
  }

  @Nullable
  protected abstract PsiElement getSeparator();

  @Nullable
  protected abstract PsiElement getReferenceNameElement();

  @Override
  public TextRange getRangeInElement() {
    final PsiElement element = getSeparator();
    final int length = getTextLength();
    return element == null ? TextRange.from(0, length) : new TextRange(element.getStartOffsetInParent() + element.getTextLength(), length);
  }

  @Override
  @Nullable
  @NonNls
  public String getReferenceName() {
    final PsiElement element = getReferenceNameElement();
    return element == null ? null : element.getText().trim();
  }

  @Override
  public final boolean isSoft() {
    return false;
  }

  protected abstract static class AbstractQualifiedReferenceResolvingProcessor implements PsiScopeProcessor {
    private boolean myFound;
    private final Set<ResolveResult> myResults = new LinkedHashSet<>();

    @Override
    public boolean execute(@NotNull final PsiElement element, @NotNull final ResolveState state) {
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

    @Override
    public void handleEvent(@NotNull final Event event, final Object associated) {
      if ((event == JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT || event == Event.SET_DECLARATION_HOLDER) && !myResults.isEmpty()) {
        setFound();
      }
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
