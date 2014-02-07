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
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeCompatibilityConstraint;
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
    return FunctionalInterfaceParameterizationUtil.getGroundTargetType(LambdaUtil.getFunctionalInterfaceType(this, true));
  }

  @Override
  public boolean isExact() {
    return getPotentiallyApplicableMember() != null;
  }

  public PsiMember getPotentiallyApplicableMember() {
    return CachedValuesManager.getCachedValue(this, new CachedValueProvider<PsiMember>() {
      @Nullable
      @Override
      public Result<PsiMember> compute() {
        return Result.createSingleDependency(getPotentiallyApplicableMemberInternal(),
                                             PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }
    });
  }

  private PsiMember getPotentiallyApplicableMemberInternal() {
    final PsiElement element = getReferenceNameElement();
    final PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult = PsiMethodReferenceUtil.getQualifierResolveResult(this);
    final PsiClass containingClass = qualifierResolveResult.getContainingClass();
    if (containingClass != null) {
      PsiMethod[] methods = null;
      if (element instanceof PsiIdentifier) {
        methods = containingClass.findMethodsByName(element.getText(), false);
      }
      else if (isConstructor()) {
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
        final PsiClass arrayClass = factory.getArrayClass(PsiUtil.getLanguageLevel(this));
        if (arrayClass == containingClass) {
          final PsiType componentType = qualifierResolveResult.getSubstitutor().substitute(arrayClass.getTypeParameters()[0]);
          LOG.assertTrue(componentType != null, qualifierResolveResult.getSubstitutor());
          methods = new PsiMethod[] {factory.createMethodFromText("public " + componentType.createArrayType().getCanonicalText() + " __array__(int i) {return null;}", this)};
        } else {
          methods = containingClass.getConstructors();
        }
      }
      if (methods != null) {
        PsiMethod psiMethod = null;
        if (methods.length > 0) {
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
        }
        if (containingClass.isPhysical() && containingClass.hasTypeParameters()) {
          final PsiElement qualifier = getQualifier();
          if (qualifier instanceof PsiTypeElement) {
            final PsiJavaCodeReferenceElement referenceElement = ((PsiTypeElement)qualifier).getInnermostComponentReferenceElement();
            if (referenceElement != null) {
              final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
              if (parameterList == null || parameterList.getTypeParameterElements().length == 0) {
                return null;
              }
            }
          } else if (qualifier instanceof PsiReferenceExpression) {
            final PsiReferenceParameterList parameterList = ((PsiReferenceExpression)qualifier).getParameterList();
            if (parameterList == null || parameterList.getTypeParameterElements().length == 0) {
              return null;
            }
          }
        }
        return psiMethod == null ? containingClass : psiMethod;
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
      final PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult = PsiMethodReferenceUtil.getQualifierResolveResult(
        PsiMethodReferenceExpressionImpl.this);

      final PsiClass containingClass = qualifierResolveResult.getContainingClass();
      PsiSubstitutor substitutor = qualifierResolveResult.getSubstitutor();

      if (containingClass != null) {
        final PsiElement element = getReferenceNameElement();
        final boolean isConstructor = isConstructor();
        if (element instanceof PsiIdentifier || isConstructor) {
          if (isConstructor && (containingClass.isEnum() || containingClass.hasModifierProperty(PsiModifier.ABSTRACT))) {
            return JavaResolveResult.EMPTY_ARRAY;
          }
          PsiType functionalInterfaceType = null;
          final Map<PsiMethodReferenceExpression,PsiType> map = PsiMethodReferenceUtil.ourRefs.get();
          if (map != null) {
            functionalInterfaceType = FunctionalInterfaceParameterizationUtil.getGroundTargetType(map.get(PsiMethodReferenceExpressionImpl.this));
          }
          if (functionalInterfaceType == null) {
            functionalInterfaceType = getFunctionalInterfaceType();
          }
          final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
          final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
          final PsiSubstitutor functionalInterfaceSubstitutor = interfaceMethod != null ? LambdaUtil.getSubstitutor(interfaceMethod, resolveResult) : null;
          final MethodSignature signature = interfaceMethod != null ? interfaceMethod.getSignature(functionalInterfaceSubstitutor) : null;
          final PsiType interfaceMethodReturnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);
          PsiFile containingFile = getContainingFile();
          final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(containingFile);
          if (isConstructor && interfaceMethod != null) {
            final PsiTypeParameter[] typeParameters = containingClass.getTypeParameters();
            final boolean isRawSubst = PsiUtil.isRawSubstitutor(containingClass, substitutor);
            final PsiClassType returnType = JavaPsiFacade.getElementFactory(containingClass.getProject())
              .createType(containingClass, isRawSubst ? PsiSubstitutor.EMPTY : substitutor);

            final InferenceSession session = new InferenceSession(typeParameters, substitutor, getManager(), null);
            if (!(session.isProperType(returnType) && session.isProperType(interfaceMethodReturnType))) {
              session.registerConstraints(returnType, interfaceMethodReturnType);
              substitutor = session.infer();
            }

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
                final PsiType[] typeParameters = PsiMethodReferenceExpressionImpl.this.getTypeParameters();
                return new MethodCandidateInfo(method, substitutor, !accessible, staticProblem, argumentList, myCurrentFileContext,
                                               argumentList != null ? argumentList.getExpressionTypes() : null, typeParameters.length > 0 ? typeParameters : null,
                                               getLanguageLevel()) {
                  @NotNull
                  @Override
                  public PsiSubstitutor inferTypeArguments(@NotNull ParameterTypeInferencePolicy policy, boolean includeReturnConstraint) {
                    if (functionalInterfaceSubstitutor == null) return PsiSubstitutor.EMPTY;
                    final InferenceSession session = new InferenceSession(method.getTypeParameters(), substitutor, getManager(), PsiMethodReferenceExpressionImpl.this);
                    final PsiParameter[] functionalMethodParameters = interfaceMethod.getParameterList().getParameters();
                    final PsiParameter[] parameters = method.getParameterList().getParameters();
                    if (parameters.length == functionalMethodParameters.length) {//static methods
                      for (int i = 0; i < functionalMethodParameters.length; i++) {
                        final PsiType pType = functionalInterfaceSubstitutor.substitute(functionalMethodParameters[i].getType());
                        session.addConstraint(new TypeCompatibilityConstraint(parameters[i].getType(), pType));
                      }
                    }
                    else if (parameters.length + 1 == functionalMethodParameters.length) { //instance methods
                      final PsiClass aClass = qualifierResolveResult.getContainingClass();
                      session.initBounds(aClass.getTypeParameters());
                      final PsiSubstitutor qualifierResultSubstitutor = qualifierResolveResult.getSubstitutor();

                      final PsiType pType = functionalInterfaceSubstitutor.substitute(functionalMethodParameters[0].getType());

                      PsiSubstitutor psiSubstitutor = qualifierResultSubstitutor;
                      //15.28.1 If the ReferenceType is a raw type, and there exists a parameterization of this type, T, that is a supertype of P1,
                      // the type to search is the result of capture conversion (5.1.10) applied to T; 
                      // otherwise, the type to search is the same as the type of the first search. Again, the type arguments, if any, are given by the method reference.
                      if (PsiUtil.isRawSubstitutor(aClass, qualifierResultSubstitutor)) {
                        final PsiClassType.ClassResolveResult pResult = PsiUtil.resolveGenericsClassInType(pType);
                        final PsiClass pClass = pResult.getElement();
                        final PsiSubstitutor receiverSubstitutor = pClass != null ? TypeConversionUtil.getClassSubstitutor(aClass, pClass, pResult.getSubstitutor()) : null;
                        if (receiverSubstitutor != null) {
                          psiSubstitutor = receiverSubstitutor;
                        }
                      }

                      final PsiType qType = JavaPsiFacade.getElementFactory(getProject()).createType(aClass, psiSubstitutor);

                      session.addConstraint(new TypeCompatibilityConstraint(qType, pType));
                      
                      for (int i = 0; i < parameters.length; i++) {
                        final PsiType interfaceParamType = functionalInterfaceSubstitutor.substitute(functionalMethodParameters[i + 1].getType());
                        session.addConstraint(new TypeCompatibilityConstraint(parameters[i].getType(), interfaceParamType));
                      }
                    }
                    else {
                      return substitutor;
                    }

                    boolean success = session.repeatInferencePhases(false);

                    final PsiType returnType = method.getReturnType();
                    if (returnType != null) {
                      session.registerConstraints(returnType, interfaceMethodReturnType);
                    }
                    return session.infer(parameters, null, null);
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

    private class MethodReferenceConflictResolver implements PsiConflictResolver {
      private final MethodSignature mySignature;
      private final PsiMethodReferenceUtil.QualifierResolveResult myQualifierResolveResult;
      private final boolean myFunctionalMethodVarArgs;

      private MethodReferenceConflictResolver(PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult,
                                              @Nullable MethodSignature signature, boolean varArgs) {
        myQualifierResolveResult = qualifierResolveResult;
        myFunctionalMethodVarArgs = varArgs;
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
          PsiSubstitutor substitutor = conflict.getSubstitutor();
          final PsiType[] signatureParameterTypes2 = psiMethod.getSignature(substitutor).getParameterTypes();

          final boolean varArgs = psiMethod.isVarArgs();

          if (parameterTypes.length == signatureParameterTypes2.length || (varArgs && !myFunctionalMethodVarArgs && Math.abs(parameterTypes.length - signatureParameterTypes2.length) <= 1)) {
            boolean correct = true;
            for (int i = 0; i < parameterTypes.length; i++) {
              final PsiType type1 = substitutor.substitute(GenericsUtil.eliminateWildcards(parameterTypes[i]));
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
              final PsiType type1 = substitutor.substitute(GenericsUtil.eliminateWildcards(parameterTypes[i + 1]));
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
        checkSameSignatures(conflicts);
        if (conflicts.size() == 1) return conflicts.get(0);

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
      protected boolean nonComparable(CandidateInfo method, CandidateInfo conflict) {
        if (method == conflict) return true;
        PsiElement psiElement = method.getElement();
        PsiElement conflictElement = conflict.getElement();
        if (psiElement instanceof PsiMethod && conflictElement instanceof PsiMethod) {
          if (((PsiMethod)psiElement).getParameterList().getParametersCount() !=
              ((PsiMethod)conflictElement).getParameterList().getParametersCount()) {
            return true;
          }
        }
        return false;
      }
    }
  }

  @Override
  public boolean isConstructor() {
    final PsiElement element = getReferenceNameElement();
    return element instanceof PsiKeyword && PsiKeyword.NEW.equals(element.getText());
  }
}
