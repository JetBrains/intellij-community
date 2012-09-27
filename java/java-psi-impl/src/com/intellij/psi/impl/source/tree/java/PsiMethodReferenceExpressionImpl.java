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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

public class PsiMethodReferenceExpressionImpl extends PsiReferenceExpressionBase implements PsiMethodReferenceExpression {
  private static Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiMethodReferenceExpressionImpl");

  public PsiMethodReferenceExpressionImpl() {
    super(JavaElementType.METHOD_REF_EXPRESSION);
  }

  @Override
  public PsiTypeElement getQualifierType() {
    final PsiElement qualifier = getQualifier();
    return qualifier instanceof PsiTypeElement ? (PsiTypeElement)qualifier : null;
  }

  @Nullable
  @Override
  public PsiType getFunctionalInterfaceType() {
    return LambdaUtil.getFunctionalInterfaceType(this, true);
  }

  @Override
  public PsiExpression getQualifierExpression() {
    final PsiElement qualifier = getQualifier();
    return qualifier instanceof PsiExpression ? (PsiExpression)qualifier : null;
  }

  @Override
  public PsiType getType() {
    return new PsiMethodReferenceType(this);
  }

  @Override
  public PsiElement getReferenceNameElement() {
    final PsiElement element = getLastChild();
    return element instanceof PsiIdentifier || PsiUtil.isJavaToken(element, JavaTokenType.NEW_KEYWORD) ? element : null;
  }

  @Override
  public void processVariants(final PsiScopeProcessor processor) {
    final FilterScopeProcessor proc = new FilterScopeProcessor(ElementClassFilter.METHOD, processor);
    PsiScopesUtil.resolveAndWalk(proc, this, null, true);
  }

  @Override
  public int getChildRole(ASTNode child) {
    final IElementType elType = child.getElementType();
    if (elType == JavaTokenType.DOUBLE_COLON) {
      return ChildRole.DOUBLE_COLON;
    }
    return ChildRole.EXPRESSION;
  }

  @NotNull
  @Override
  public JavaResolveResult[] multiResolve(final boolean incompleteCode) {
    final PsiManagerEx manager = getManager();
    if (manager == null) {
      LOG.error("getManager() == null!");
      return JavaResolveResult.EMPTY_ARRAY;
    }
    if (!isValid()) {
      LOG.error("invalid!");
      return JavaResolveResult.EMPTY_ARRAY;
    }
    ResolveResult[] results = ResolveCache.getInstance(getProject()).resolveWithCaching(this, new MethodReferenceResolver(), true, incompleteCode);
    return results.length == 0 ? JavaResolveResult.EMPTY_ARRAY : (JavaResolveResult[])results;
  }

  @Override
  public PsiElement getQualifier() {
    final PsiElement element = getFirstChild();
    return element instanceof PsiExpression || element instanceof PsiTypeElement ? element : null;
  }

  @Override
  public TextRange getRangeInElement() {
    final PsiElement element = getReferenceNameElement();
    if (element != null) {
      final int offsetInParent = element.getStartOffsetInParent();
      return new TextRange(offsetInParent, offsetInParent + element.getTextLength());
    }
    final PsiElement colons = findPsiChildByType(JavaTokenType.DOUBLE_COLON);
    if (colons != null) {
      final int offsetInParent = colons.getStartOffsetInParent();
      return new TextRange(offsetInParent, offsetInParent + colons.getTextLength());
    }
    LOG.error(getText());
    return null;
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return getText();
  }

  @Override
  public boolean isReferenceTo(final PsiElement element) {
    if (!(element instanceof PsiMethod)) return false;
    final PsiMethod method = (PsiMethod)element;

    final PsiElement nameElement = getReferenceNameElement();
    if (nameElement instanceof PsiIdentifier) {
      if (!nameElement.getText().equals(method.getName())) return false;
    }
    else if (PsiUtil.isJavaToken(nameElement, JavaTokenType.NEW_KEYWORD)) {
      if (!method.isConstructor()) return false;
    }

    return element.getManager().areElementsEquivalent(element, resolve());
  }

  @Override
  public void accept(@NotNull final PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitMethodReferenceExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiMethodReferenceExpression:" + getText();
  }

  @Override
  public void process(Ref<PsiClass> psiClassRef, Ref<PsiSubstitutor> substRef) {
    PsiClass containingClass = null;
    final PsiExpression expression = getQualifierExpression();
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (expression != null) {
      PsiClassType.ClassResolveResult result = PsiUtil.resolveGenericsClassInType(expression.getType());
      containingClass = result.getElement();
      if (containingClass != null) {
        substitutor = result.getSubstitutor();
      }
      if (containingClass == null && expression instanceof PsiReferenceExpression) {
        final PsiElement resolve = ((PsiReferenceExpression)expression).resolve();
        if (resolve instanceof PsiClass) {
          containingClass = (PsiClass)resolve;
        }
      }
    }
    else {
      final PsiTypeElement typeElement = getQualifierType();
      if (typeElement != null) {
        PsiClassType.ClassResolveResult result = PsiUtil.resolveGenericsClassInType(typeElement.getType());
        containingClass = result.getElement();
        if (containingClass != null) {
          substitutor = result.getSubstitutor();
        }
      }
    }
    psiClassRef.set(containingClass);
    substRef.set(substitutor);
  }
  
  private class MethodReferenceResolver implements ResolveCache.PolyVariantResolver<PsiJavaReference> {
    @NotNull
    @Override
    public ResolveResult[] resolve(@NotNull PsiJavaReference reference, boolean incompleteCode) {
      final Ref<PsiClass> classRef = new Ref<PsiClass>();
      final Ref<PsiSubstitutor> substRef = new Ref<PsiSubstitutor>();
      process(classRef, substRef);
      
      final PsiClass containingClass = classRef.get();
      final PsiSubstitutor substitutor = substRef.get();
      
      if (containingClass != null) {
        final PsiElement element = getReferenceNameElement();
        if (element instanceof PsiIdentifier) {
          final PsiType functionalInterfaceType = getFunctionalInterfaceType();
          final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
          final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
          final MethodReferenceConflictResolver conflictResolver = new MethodReferenceConflictResolver(containingClass, substitutor, interfaceMethod != null ? interfaceMethod.getSignature(resolveResult.getSubstitutor()) : null);
          final MethodCandidatesProcessor processor = new MethodCandidatesProcessor(PsiMethodReferenceExpressionImpl.this, 
                                                                                    new PsiConflictResolver[]{conflictResolver}, new SmartList<CandidateInfo>());
          processor.setIsConstructor(false);
          processor.setName(element.getText());
          
          ResolveState state = ResolveState.initial().put(PsiSubstitutor.KEY, substitutor);
          containingClass.processDeclarations(processor, state, 
                                              PsiMethodReferenceExpressionImpl.this,
                                              PsiMethodReferenceExpressionImpl.this);
          return processor.getResult();
        }
      }
      return JavaResolveResult.EMPTY_ARRAY;
    }

    private class MethodReferenceConflictResolver implements PsiConflictResolver {
      private final PsiClass myContainingClass;
      private final PsiSubstitutor mySubstitutor;
      private final MethodSignature mySignature;
      
      private MethodReferenceConflictResolver(PsiClass containingClass,
                                              PsiSubstitutor psiSubstitutor,
                                              @Nullable MethodSignature signature) {
        myContainingClass = containingClass;
        mySubstitutor = psiSubstitutor;
        mySignature = signature;
      }

      @Nullable
      @Override
      public CandidateInfo resolveConflict(List<CandidateInfo> conflicts) {
        if (mySignature == null) return null;

        for (Iterator<CandidateInfo> iterator = conflicts.iterator(); iterator.hasNext(); ) {
          CandidateInfo conflict = iterator.next();
          if (!(conflict instanceof MethodCandidateInfo)) continue;
          final PsiMethod psiMethod = ((MethodCandidateInfo)conflict).getElement();
          if (psiMethod == null) continue;
          PsiSubstitutor subst = PsiSubstitutor.EMPTY;
          subst = subst.putAll(mySubstitutor);
          if (!LambdaUtil.areAcceptable(mySignature,
                                        psiMethod.getSignature(subst.putAll(conflict.getSubstitutor())), myContainingClass, mySubstitutor)) {
            iterator.remove();
          }
        }
        if (conflicts.size() == 1) return conflicts.get(0);
        return null;
      }
    }
  }
}
