/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.ClassCandidateInfo;
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
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PsiMethodReferenceExpressionImpl extends PsiReferenceExpressionBase implements PsiMethodReferenceExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiMethodReferenceExpressionImpl");

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
  public boolean isExact() {
    return getPotentiallyApplicableMember() != null;
  }

  public PsiMember getPotentiallyApplicableMember() {
    final PsiElement element = getReferenceNameElement();
    final PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult = PsiMethodReferenceUtil.getQualifierResolveResult(this);
    final PsiClass containingClass = qualifierResolveResult.getContainingClass();
    if (containingClass != null) {
      PsiMethod[] methods = null;
      if (element instanceof PsiIdentifier) {
        methods = containingClass.findMethodsByName(element.getText(), false);
      }
      else if (element instanceof PsiKeyword && PsiKeyword.NEW.equals(element.getText())) {
        methods = containingClass.getConstructors();
        if (methods.length == 0) { //default constructor
          return containingClass;
        }
      }
      if (methods != null) {
        PsiMethod psiMethod = null;
        for (PsiMethod method : methods) {
          if (PsiUtil.isAccessible(method, this, null)) {
            if (psiMethod != null) return null;
            psiMethod = method;
          }
        }
        if (psiMethod == null) return null;
        if (psiMethod.isVarArgs()) return null;
        if (psiMethod.getTypeParameters().length > 0) {
          final PsiReferenceParameterList parameterList = getParameterList();
          return parameterList != null && parameterList.getTypeParameterElements().length > 0 ? psiMethod : null;
        }
        return psiMethod;
      }
    }
    return null;
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
  public void processVariants(@NotNull final PsiScopeProcessor processor) {
    final FilterScopeProcessor proc = new FilterScopeProcessor(ElementClassFilter.METHOD, processor);
    PsiScopesUtil.resolveAndWalk(proc, this, null, true);
  }

  @Override
  public void setQualifierExpression(@Nullable PsiExpression newQualifier) throws IncorrectOperationException {
    if (newQualifier == null) {
      super.setQualifierExpression(newQualifier);
      return;
    }
    final PsiExpression expression = getQualifierExpression();
    if (expression != null) {
      expression.replace(newQualifier);
    } else {
      final PsiElement qualifier = getQualifier();
      if (qualifier != null) {
        qualifier.replace(newQualifier);
      }
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    final IElementType elType = child.getElementType();
    if (elType == JavaTokenType.DOUBLE_COLON) {
      return ChildRole.DOUBLE_COLON;
    } else if (elType == JavaTokenType.IDENTIFIER) {
      return ChildRole.REFERENCE_NAME;
    } else if (elType == JavaElementType.REFERENCE_EXPRESSION) {
      return ChildRole.CLASS_REFERENCE;
    }
    return ChildRole.EXPRESSION;
  }

  @NotNull
  @Override
  public JavaResolveResult[] multiResolve(final boolean incompleteCode) {
    FileElement fileElement = SharedImplUtil.findFileElement(this);
    if (fileElement == null) {
      LOG.error("fileElement == null!");
      return JavaResolveResult.EMPTY_ARRAY;
    }
    final PsiManagerEx manager = fileElement.getManager();
    if (manager == null) {
      LOG.error("getManager() == null!");
      return JavaResolveResult.EMPTY_ARRAY;
    }
    PsiFile file = SharedImplUtil.getContainingFile(fileElement);
    boolean valid = file != null && file.isValid();
    if (!valid) {
      LOG.error("invalid!");
      return JavaResolveResult.EMPTY_ARRAY;
    }
    final MethodReferenceResolver resolver = new MethodReferenceResolver();
    final Map<PsiMethodReferenceExpression, PsiType> map = PsiMethodReferenceUtil.ourRefs.get();
    if (map != null && map.containsKey(this)) {
      return (JavaResolveResult[])resolver.resolve(this, incompleteCode);
    }
    ResolveResult[] results = ResolveCache.getInstance(getProject()).resolveWithCaching(this, resolver, true, incompleteCode,file);
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
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return this;
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    PsiElement oldIdentifier = findChildByRoleAsPsiElement(ChildRole.REFERENCE_NAME);
    if (oldIdentifier == null) {
      oldIdentifier = findChildByRoleAsPsiElement(ChildRole.CLASS_REFERENCE);
    }
    if (oldIdentifier == null) {
      throw new IncorrectOperationException();
    }
    final String oldRefName = oldIdentifier.getText();
    if (PsiKeyword.THIS.equals(oldRefName) ||
        PsiKeyword.SUPER.equals(oldRefName) ||
        PsiKeyword.NEW.equals(oldRefName) ||
        Comparing.strEqual(oldRefName, newElementName)) {
      return this;
    }
    PsiIdentifier identifier = JavaPsiFacade.getInstance(getProject()).getElementFactory().createIdentifier(newElementName);
    oldIdentifier.replace(identifier);
    return this;
  }

  @Override
  public String toString() {
    return "PsiMethodReferenceExpression:" + getText();
  }

  private boolean isLocatedInStaticContext(PsiClass containingClass) {
    final PsiClass gContainingClass = containingClass.getContainingClass();
    if (gContainingClass == null || !containingClass.hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass aClass = null;
      if (PsiTreeUtil.isAncestor(gContainingClass != null ? gContainingClass : containingClass, this, false)) {
        aClass = gContainingClass != null ? gContainingClass : containingClass;
      }
      if (PsiUtil.getEnclosingStaticElement(this, aClass) != null) {
        return true;
      }
    }
    return false;
  }

  private class MethodReferenceResolver implements ResolveCache.PolyVariantResolver<PsiJavaReference> {
    @NotNull
    @Override
    public ResolveResult[] resolve(@NotNull PsiJavaReference reference, boolean incompleteCode) {
      final PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult = PsiMethodReferenceUtil.getQualifierResolveResult(PsiMethodReferenceExpressionImpl.this);

      final PsiClass containingClass = qualifierResolveResult.getContainingClass();
      PsiSubstitutor substitutor = qualifierResolveResult.getSubstitutor();

      if (containingClass != null) {
        final PsiElement element = getReferenceNameElement();
        final boolean isConstructor = element instanceof PsiKeyword && PsiKeyword.NEW.equals(element.getText());
        if (element instanceof PsiIdentifier || isConstructor) {
          if (isConstructor && (containingClass.isEnum() || containingClass.hasModifierProperty(PsiModifier.ABSTRACT))) {
            return JavaResolveResult.EMPTY_ARRAY;
          }
          PsiType functionalInterfaceType = null;
          final Map<PsiMethodReferenceExpression,PsiType> map = PsiMethodReferenceUtil.ourRefs.get();
          if (map != null) {
            functionalInterfaceType = map.get(PsiMethodReferenceExpressionImpl.this);
          }
          if (functionalInterfaceType == null) {
            functionalInterfaceType = getFunctionalInterfaceType();
          }
          final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
          final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
          final MethodSignature signature = interfaceMethod != null ? interfaceMethod.getSignature(LambdaUtil.getSubstitutor(interfaceMethod, resolveResult)) : null;
          final PsiType interfaceMethodReturnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);
          PsiFile containingFile = getContainingFile();
          final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(containingFile);
          if (isConstructor && interfaceMethod != null) {
            final PsiTypeParameter[] typeParameters = containingClass.getTypeParameters();
            final boolean isRawSubst = PsiUtil.isRawSubstitutor(containingClass, substitutor);
            Project project = containingClass.getProject();
            final PsiClassType returnType = JavaPsiFacade.getElementFactory(project).createType(containingClass,
                                                                                                     isRawSubst ? PsiSubstitutor.EMPTY : substitutor);

            substitutor = LambdaUtil.inferFromReturnType(typeParameters, returnType, interfaceMethodReturnType, substitutor, languageLevel,
                                                         project);

            if (containingClass.getConstructors().length == 0) {
              ClassCandidateInfo candidateInfo = null;
              if ((containingClass.getContainingClass() == null || !isLocatedInStaticContext(containingClass)) && signature.getParameterTypes().length == 0 ||
                  PsiMethodReferenceUtil.onArrayType(containingClass, signature)) {
                candidateInfo = new ClassCandidateInfo(containingClass, substitutor);
              }
              return candidateInfo == null ? JavaResolveResult.EMPTY_ARRAY : new JavaResolveResult[]{candidateInfo};
            }
          }

          final MethodReferenceConflictResolver conflictResolver =
            new MethodReferenceConflictResolver(qualifierResolveResult, signature, interfaceMethod != null && interfaceMethod.isVarArgs());
          final PsiConflictResolver[] resolvers;
          if (signature != null) {
            final PsiType[] parameterTypes = signature.getParameterTypes();
            resolvers = new PsiConflictResolver[]{conflictResolver, new MethodRefsSpecificResolver(parameterTypes, languageLevel)};
          }
          else {
            resolvers = new PsiConflictResolver[]{conflictResolver};
          }
          final MethodCandidatesProcessor processor =
            new MethodCandidatesProcessor(PsiMethodReferenceExpressionImpl.this, containingFile, resolvers, new SmartList<CandidateInfo>()) {
              @Override
              protected MethodCandidateInfo createCandidateInfo(final PsiMethod method,
                                                                final PsiSubstitutor substitutor,
                                                                final boolean staticProblem,
                                                                final boolean accessible) {
                final PsiExpressionList argumentList = getArgumentList();
                return new MethodCandidateInfo(method, substitutor, !accessible, staticProblem, argumentList, myCurrentFileContext,
                                               argumentList != null ? argumentList.getExpressionTypes() : null, getTypeArguments(),
                                               getLanguageLevel()) {
                  @Override
                  public PsiSubstitutor inferTypeArguments(@NotNull ParameterTypeInferencePolicy policy, boolean includeReturnConstraint) {
                    return inferTypeArgumentsFromInterfaceMethod(signature, interfaceMethodReturnType, method, substitutor, languageLevel);
                  }
                };
              }
          };
          processor.setIsConstructor(isConstructor);
          processor.setName(isConstructor ? containingClass.getName() : element.getText());
          final PsiExpression expression = getQualifierExpression();
          if (expression == null || !(expression.getType() instanceof PsiArrayType)) {
            processor.setAccessClass(containingClass);
          }

          if (qualifierResolveResult.isReferenceTypeQualified() && isLocatedInStaticContext(containingClass)) {
             processor.handleEvent(JavaScopeProcessorEvent.START_STATIC, null);
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

    private PsiSubstitutor inferTypeArgumentsFromInterfaceMethod(@Nullable MethodSignature signature,
                                                                 @Nullable PsiType interfaceMethodReturnType,
                                                                 PsiMethod method,
                                                                 PsiSubstitutor substitutor,
                                                                 LanguageLevel languageLevel) {
      if (signature == null) return PsiSubstitutor.EMPTY;
      PsiType[] types = method.getSignature(PsiUtil.isRawSubstitutor(method, substitutor) ? PsiSubstitutor.EMPTY : substitutor).getParameterTypes();
      PsiType[] rightTypes = signature.getParameterTypes();
      if (!method.isVarArgs() || types.length == 0) {
        if (types.length < rightTypes.length) {
          return getSubstitutor(rightTypes[0]);
        } else if (types.length > rightTypes.length) {
          return getSubstitutor(types[0]);
        }
      } else {
        if (rightTypes.length != types.length || rightTypes[rightTypes.length - 1].getArrayDimensions() != types[types.length-1].getArrayDimensions()) {
          types[types.length - 1] = ((PsiArrayType)types[types.length - 1]).getComponentType();
          int min = Math.min(types.length, rightTypes.length);
          types = Arrays.copyOf(types, min);
          rightTypes = Arrays.copyOf(rightTypes, min);
        }
      }

      for (int i = 0; i < rightTypes.length; i++) {
        rightTypes[i] = GenericsUtil.eliminateWildcards(rightTypes[i]);
      }
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(getProject()).getResolveHelper();
      PsiSubstitutor psiSubstitutor = resolveHelper.inferTypeArguments(method.getTypeParameters(), types, rightTypes, languageLevel);
      if (method.isConstructor()) {
        psiSubstitutor = psiSubstitutor.putAll(resolveHelper.inferTypeArguments(method.getContainingClass().getTypeParameters(), types, rightTypes, languageLevel));
      }
      if (!PsiUtil.isRawSubstitutor(method, substitutor)) {
        psiSubstitutor = psiSubstitutor.putAll(substitutor);
      }
      return LambdaUtil.inferFromReturnType(method.getTypeParameters(),
                                            psiSubstitutor.substitute(method.getReturnType()),
                                            interfaceMethodReturnType,
                                            psiSubstitutor,
                                            languageLevel, getProject());
    }

    private PsiSubstitutor getSubstitutor(PsiType type) {
      final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(GenericsUtil.eliminateWildcards(type));
      PsiSubstitutor psiSubstitutor = resolveResult.getSubstitutor();
      if (type instanceof PsiClassType) {
        final PsiClass psiClass = resolveResult.getElement();
        if (psiClass instanceof PsiTypeParameter) {
          for (PsiClass aClass : psiClass.getSupers()) {
            psiSubstitutor = psiSubstitutor.putAll(TypeConversionUtil.getSuperClassSubstitutor(aClass, (PsiClassType)type));
          }
        }
      }
      return psiSubstitutor;
    }

    private class MethodReferenceConflictResolver implements PsiConflictResolver {
      private final PsiSubstitutor mySubstitutor;
      private final MethodSignature mySignature;
      private final PsiMethodReferenceUtil.QualifierResolveResult myQualifierResolveResult;
      private final boolean myFunctionalMethodVarArgs;

      private MethodReferenceConflictResolver(PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult,
                                              @Nullable MethodSignature signature, boolean varArgs) {
        myQualifierResolveResult = qualifierResolveResult;
        myFunctionalMethodVarArgs = varArgs;
        mySubstitutor = qualifierResolveResult.getSubstitutor();
        mySignature = signature;
      }

      @Nullable
      @Override
      public CandidateInfo resolveConflict(@NotNull List<CandidateInfo> conflicts) {
        if (mySignature == null) return null;

        final PsiType[] parameterTypes = mySignature.getParameterTypes();
        boolean hasReceiver = PsiMethodReferenceUtil.hasReceiver(parameterTypes, myQualifierResolveResult,
                                                                 PsiMethodReferenceExpressionImpl.this);

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

          if (parameterTypes.length == signatureParameterTypes2.length || (varArgs && !myFunctionalMethodVarArgs && Math.abs(parameterTypes.length - signatureParameterTypes2.length) <= 1)) {
            boolean correct = true;
            for (int i = 0; i < parameterTypes.length; i++) {
              final PsiType type1 = subst.substitute(GenericsUtil.eliminateWildcards(parameterTypes[i]));
              if (varArgs && i >= signatureParameterTypes2.length - 1) {
                final PsiType type2 = signatureParameterTypes2[signatureParameterTypes2.length - 1];
                correct &= TypeConversionUtil.isAssignable(type2, type1) || TypeConversionUtil.isAssignable(((PsiArrayType)type2).getComponentType(), type1);
              }
              else {
                correct &= TypeConversionUtil.isAssignable(signatureParameterTypes2[i], type1);
              }
            }
            if (correct) {
              firstCandidates.add(conflict);
            }
          }

          if (hasReceiver && parameterTypes.length == signatureParameterTypes2.length + 1) {
            boolean correct = true;
            for (int i = 0; i < signatureParameterTypes2.length; i++) {
              final PsiType type1 = subst.substitute(GenericsUtil.eliminateWildcards(parameterTypes[i + 1]));
              final PsiType type2 = signatureParameterTypes2[i];
              final boolean assignable = TypeConversionUtil.isAssignable(type2, type1);
              if (varArgs && i == signatureParameterTypes2.length - 1) {
                correct &= assignable || TypeConversionUtil.isAssignable(((PsiArrayType)type2).getComponentType(), type1);
              }
              else {
                correct &= assignable;
              }
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
          conflicts.clear();
          conflicts.addAll(firstCandidates);
          return null;
        }
        return !firstCandidates.isEmpty() ? firstCandidates.get(0) : secondCandidates.get(0);
      }
    }

    private class MethodRefsSpecificResolver extends JavaMethodsConflictResolver {
      public MethodRefsSpecificResolver(@NotNull PsiType[] parameterTypes, @NotNull LanguageLevel languageLevel) {
        super(PsiMethodReferenceExpressionImpl.this, parameterTypes, languageLevel);
      }

      @Override
      public CandidateInfo resolveConflict(@NotNull List<CandidateInfo> conflicts) {
        checkAccessStaticLevels(conflicts, true);
        if (conflicts.size() == 1) return conflicts.get(0);

        boolean varargs = false;
        boolean fixedArity = false;
        for (CandidateInfo conflict : conflicts) {
          final PsiElement psiElement = conflict.getElement();
          if (psiElement instanceof PsiMethod) {
            final boolean isVarargs = ((PsiMethod)psiElement).isVarArgs();
            if (isVarargs) {
              varargs = true;
            } else {
              fixedArity = true;
            }
            if (varargs && fixedArity) break;
          }
        }
        if (varargs && fixedArity) {
          for (Iterator<CandidateInfo> iterator = conflicts.iterator(); iterator.hasNext(); ) {
            CandidateInfo conflict = iterator.next();
            final PsiElement element = conflict.getElement();
            if (element instanceof PsiMethod && ((PsiMethod)element).isVarArgs()) iterator.remove();
          }
        }

        checkSpecifics(conflicts,
                       varargs ? MethodCandidateInfo.ApplicabilityLevel.VARARGS : MethodCandidateInfo.ApplicabilityLevel.FIXED_ARITY,
                       myLanguageLevel);
        return conflicts.size() == 1 ? conflicts.get(0) : null;
      }

      @Override
      protected boolean checkSameConflicts(CandidateInfo method, CandidateInfo conflict) {
        return method == conflict;
      }
    }
  }
}
