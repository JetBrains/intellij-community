/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.source.codeStyle.ReferenceAdjuster;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.scope.BaseScopeProcessor;
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
public abstract class AbstractQualifiedReference<T extends AbstractQualifiedReference<T>> extends ASTWrapperPsiElement implements PsiPolyVariantReference, PsiQualifiedReference {
  private static final ResolveCache.PolyVariantResolver<AbstractQualifiedReference> MY_RESOLVER = new ResolveCache.PolyVariantResolver<AbstractQualifiedReference>() {
    public ResolveResult[] resolve(final AbstractQualifiedReference expression, final boolean incompleteCode) {
      return expression.resolveInner();
    }
  };

  protected AbstractQualifiedReference(@NotNull final ASTNode node) {
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
    return getManager().getResolveCache().resolveWithCaching(this, MY_RESOLVER, true, false);
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
    return psiElement == null || psiElement.processDeclarations(processor, ResolveState.initial(), null, this);
  }

  protected boolean processUnqualifiedVariants(final PsiScopeProcessor processor) {
    return PsiScopesUtil.treeWalkUp(processor, this, null);
  }


  @NotNull
  public  String getCanonicalText() {
    return getText();
  }

  @SuppressWarnings({"unchecked"})
  @Nullable
  public T getQualifier() {
    return (T)findChildByClass(getClass());
  }

  public PsiElement handleElementRename(final String newElementName) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    final PsiElement firstChildNode = ObjectUtils.assertNotNull(getFirstChild());
    final PsiElement firstInIdentifier = getClass().isInstance(firstChildNode) ? ObjectUtils.assertNotNull(firstChildNode.getNextSibling()).getNextSibling() : firstChildNode;
    getNode().removeRange(firstInIdentifier.getNode(), null);
    final PsiElement referenceName = ObjectUtils.assertNotNull(parseReference(newElementName).getReferenceNameElement());
    getNode().addChild(referenceName.getNode());
    return this;
  }

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
      if (manager.areElementsEquivalent(result.getElement(), element)) return true;
    }
    return false;
  }

  @Nullable
  protected abstract PsiElement getSeparator();

  @Nullable
  protected abstract PsiElement getReferenceNameElement();

  public TextRange getRangeInElement() {
    final PsiElement element = getSeparator();
    final int length = getTextLength();
    return element == null ? TextRange.from(0, length) : new TextRange(element.getStartOffsetInParent() + element.getTextLength(), length);
  }

  @Nullable
  @NonNls
  public String getReferenceName() {
    final PsiElement element = getReferenceNameElement();
    return element == null ? null : element.getText().trim();
  }

  public final boolean isSoft() {
    return false;
  }

  protected abstract static class AbstractQualifiedReferenceResolvingProcessor extends BaseScopeProcessor {
    private boolean myFound = false;
    private final Set<ResolveResult> myResults = new LinkedHashSet<ResolveResult>();

    public boolean execute(final PsiElement element, final ResolveState state) {
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
      if ((event == JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT || event == Event.SET_DECLARATION_HOLDER) && !myResults.isEmpty()) {
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
