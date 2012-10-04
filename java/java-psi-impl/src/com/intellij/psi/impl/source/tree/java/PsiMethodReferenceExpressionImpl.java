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
import com.intellij.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.conflictResolvers.JavaMethodsConflictResolver;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    final MethodReferenceResolver resolver = new MethodReferenceResolver();
    if (getFunctionalInterfaceType() == null) {
      return (JavaResolveResult[])resolver.resolve(this, incompleteCode);
    }
    ResolveResult[] results = ResolveCache.getInstance(getProject()).resolveWithCaching(this, resolver, true,
                                                                                        incompleteCode);
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
  public boolean process(Ref<PsiClass> psiClassRef, Ref<PsiSubstitutor> substRef) {
    PsiClass containingClass = null;
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    try {
      final PsiExpression expression = getQualifierExpression();
      if (expression != null) {
        PsiClassType.ClassResolveResult result = PsiUtil.resolveGenericsClassInType(expression.getType());
        containingClass = result.getElement();
        if (containingClass != null) {
          substitutor = result.getSubstitutor();
        }
        if (containingClass == null && expression instanceof PsiReferenceExpression) {
          final JavaResolveResult resolveResult = ((PsiReferenceExpression)expression).advancedResolve(false);
          final PsiElement resolve = resolveResult.getElement();
          if (resolve instanceof PsiClass) {
            containingClass = (PsiClass)resolve;
            substitutor = resolveResult.getSubstitutor();
            return true;
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
      return false;
    }
    finally {
      psiClassRef.set(containingClass);
      substRef.set(substitutor);
    }
  }

  private class MethodReferenceResolver implements ResolveCache.PolyVariantResolver<PsiJavaReference> {
    @NotNull
    @Override
    public ResolveResult[] resolve(@NotNull PsiJavaReference reference, boolean incompleteCode) {
      final Ref<PsiClass> classRef = new Ref<PsiClass>();
      final Ref<PsiSubstitutor> substRef = new Ref<PsiSubstitutor>();
      final boolean beginsWithReferenceType = process(classRef, substRef);

      final PsiClass containingClass = classRef.get();
      final PsiSubstitutor substitutor = substRef.get();

      if (containingClass != null) {
        final PsiElement element = getReferenceNameElement();
        final boolean isConstructor = element instanceof PsiKeyword && PsiKeyword.NEW.equals(element.getText());
        if (element instanceof PsiIdentifier || isConstructor) {
          PsiType functionalInterfaceType = getFunctionalInterfaceType();
          if (functionalInterfaceType == null) {
            final Map<PsiMethodReferenceExpression,PsiType> map = LambdaUtil.ourRefs.get();
            if (map != null) {
              functionalInterfaceType = map.get(PsiMethodReferenceExpressionImpl.this);
            }
            else {
              functionalInterfaceType = null;
            }
          }
          final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
          final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
          final MethodSignature signature = interfaceMethod != null ? interfaceMethod.getSignature(resolveResult.getSubstitutor()) : null;
          final MethodReferenceConflictResolver conflictResolver =
            new MethodReferenceConflictResolver(containingClass, substitutor, signature, beginsWithReferenceType);
          final PsiConflictResolver[] resolvers;
          if (signature != null) {
            final PsiType[] parameterTypes = signature.getParameterTypes();
            resolvers = new PsiConflictResolver[]{conflictResolver,
              new JavaMethodsConflictResolver(PsiMethodReferenceExpressionImpl.this, parameterTypes) {
                @Override
                public CandidateInfo resolveConflict(List<CandidateInfo> conflicts) {
                  boolean varargs = false;
                  for (CandidateInfo conflict : conflicts) {
                    final PsiElement psiElement = conflict.getElement();
                    if (psiElement instanceof PsiMethod && ((PsiMethod)psiElement).isVarArgs()) {
                      varargs = true;
                      break;
                    }
                  }
                  checkSpecifics(conflicts, varargs ? MethodCandidateInfo.ApplicabilityLevel.VARARGS : MethodCandidateInfo.ApplicabilityLevel.FIXED_ARITY);
                  return conflicts.size() == 1 ? conflicts.get(0) : null; 
                }
              }};
          }
          else {
            resolvers = new PsiConflictResolver[]{conflictResolver};
          }
          final MethodCandidatesProcessor processor =
            new MethodCandidatesProcessor(PsiMethodReferenceExpressionImpl.this, resolvers, new SmartList<CandidateInfo>()) {
              @Override
              protected MethodCandidateInfo createCandidateInfo(final PsiMethod method,
                                                                PsiSubstitutor substitutor,
                                                                boolean staticProblem,
                                                                boolean accessible) {
                final PsiExpressionList argumentList = getArgumentList();
                return new MethodCandidateInfo(method, substitutor, !accessible, staticProblem, argumentList, myCurrentFileContext,
                                               argumentList != null ? argumentList.getExpressionTypes() : null, getTypeArguments(),
                                               getLanguageLevel()) {
                  @Override
                  public PsiSubstitutor inferTypeArguments(ParameterTypeInferencePolicy policy) {
                    if (signature == null) return PsiSubstitutor.EMPTY;
                    final PsiType[] types = method.getSignature(PsiSubstitutor.EMPTY).getParameterTypes();
                    final PsiType[] rightTypes = signature.getParameterTypes();
                    if (types.length < rightTypes.length) {
                      return PsiUtil.resolveGenericsClassInType(rightTypes[0]).getSubstitutor();
                    } else if (types.length > rightTypes.length) {
                      return PsiUtil.resolveGenericsClassInType(types[0]).getSubstitutor();
                    }
                    return JavaPsiFacade.getInstance(getProject()).getResolveHelper()
                      .inferTypeArguments(method.getTypeParameters(), types, rightTypes,
                                          PsiUtil.getLanguageLevel(PsiMethodReferenceExpressionImpl.this));
                  }
                };
              }
          };
          processor.setIsConstructor(isConstructor);
          processor.setName(isConstructor ? containingClass.getName() : element.getText());
          processor.setAccessClass(containingClass);

          if (beginsWithReferenceType) {
            if (containingClass.getContainingClass() == null || !containingClass.hasModifierProperty(PsiModifier.STATIC)) {
              PsiClass aClass = null;
              if (PsiTreeUtil.isAncestor(containingClass, PsiMethodReferenceExpressionImpl.this, false)) {
                aClass = containingClass;
              }
              if (PsiUtil.getEnclosingStaticElement(PsiMethodReferenceExpressionImpl.this, aClass) != null) {
                processor.handleEvent(JavaScopeProcessorEvent.START_STATIC, null);
              }
            }
          }
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
      private final boolean myBeginsWithReferenceType;

      private MethodReferenceConflictResolver(PsiClass containingClass,
                                              PsiSubstitutor psiSubstitutor,
                                              @Nullable MethodSignature signature, boolean beginsWithReferenceType) {
        myContainingClass = containingClass;
        mySubstitutor = psiSubstitutor;
        mySignature = signature;
        myBeginsWithReferenceType = beginsWithReferenceType;
      }

      @Nullable
      @Override
      public CandidateInfo resolveConflict(List<CandidateInfo> conflicts) {
        if (mySignature == null) return null;

        boolean hasReceiver = false;
        final PsiType[] parameterTypes = mySignature.getParameterTypes();
        if (parameterTypes.length > 0) {
          final PsiClassType.ClassResolveResult classResolveResult = PsiUtil.resolveGenericsClassInType(parameterTypes[0]);
          if (LambdaUtil.isReceiverType(parameterTypes[0], myContainingClass, mySubstitutor)) {
            hasReceiver = true;
          }
        }

        final List<CandidateInfo> firstCandidates = new ArrayList<CandidateInfo>();
        final List<CandidateInfo> secondCandidates = new ArrayList<CandidateInfo>();
        for (CandidateInfo conflict : conflicts) {
          if (!(conflict instanceof MethodCandidateInfo)) continue;
          final PsiMethod psiMethod = ((MethodCandidateInfo)conflict).getElement();
          if (psiMethod == null) continue;
          PsiSubstitutor subst = PsiSubstitutor.EMPTY;
          subst = subst.putAll(mySubstitutor);
          subst = subst.putAll(conflict.getSubstitutor());
          final PsiType[] signatureParameterTypes2 = psiMethod.getSignature(subst).getParameterTypes();

          final boolean varArgs = psiMethod.isVarArgs();
          final boolean validConstructorRef = psiMethod.isConstructor() && (myContainingClass.getContainingClass() == null || myContainingClass.hasModifierProperty(PsiModifier.STATIC));
          final boolean staticOrValidConstructorRef = psiMethod.hasModifierProperty(PsiModifier.STATIC) || validConstructorRef;

          if ((parameterTypes.length == signatureParameterTypes2.length || varArgs && parameterTypes.length >= signatureParameterTypes2.length) && 
              (!myBeginsWithReferenceType || staticOrValidConstructorRef)) {
            boolean correct = true;
            for (int i = 0; i < parameterTypes.length; i++) {
              final PsiType type1 = parameterTypes[i];
              final PsiType type2 = varArgs && i >= signatureParameterTypes2.length - 1 ?
                                    ((PsiArrayType)signatureParameterTypes2[signatureParameterTypes2.length -1]).getComponentType() :
                                    signatureParameterTypes2[i];
              correct &= TypeConversionUtil.isAssignable(type2, subst.substitute(GenericsUtil.eliminateWildcards(type1)));
            }
            if (correct) {
              firstCandidates.add(conflict);
            }
          }

          if (hasReceiver && parameterTypes.length == signatureParameterTypes2.length + 1 && !staticOrValidConstructorRef) {
            boolean correct = true;
            for (int i = 0; i < signatureParameterTypes2.length; i++) {
              final PsiType type1 = parameterTypes[i + 1];
              final PsiType type2 = signatureParameterTypes2[i];
              correct &= TypeConversionUtil.isAssignable(type2, subst.substitute(GenericsUtil.eliminateWildcards(type1)));
            }
            if (correct) {
              secondCandidates.add(conflict);
            }
          }
        }

        final int acceptedCount = secondCandidates.size() + firstCandidates.size();
        if (acceptedCount != 1) {
          if (acceptedCount == 0) {
            conflicts.clear();
          }
          firstCandidates.addAll(secondCandidates);
          conflicts.retainAll(firstCandidates);
          return null;
        }
        return !firstCandidates.isEmpty() ? firstCandidates.get(0) : secondCandidates.get(0);
      }
    }
  }
}
