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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
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
import com.intellij.psi.scope.conflictResolvers.DuplicateConflictResolver;
import com.intellij.psi.scope.conflictResolvers.JavaMethodsConflictResolver;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.Function;
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

  private static boolean arrayCreationSignature(MethodSignature signature) {
    if (arrayCompatibleSignature(signature.getParameterTypes(), new Function<PsiType[], PsiType>() {
      @Override
      public PsiType fun(PsiType[] types) {
        return types[0];
      }
    })) {
      return true;
    }
    return false;
  }

  public static <T> boolean arrayCompatibleSignature(T[] paramTypes, Function<T[], PsiType> fun) {
    if (paramTypes.length == 1) {
      final PsiType paramType = fun.fun(paramTypes);
      if (paramType != null && TypeConversionUtil.isAssignable(PsiType.INT, paramType)) return true;
    }
    return false;
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

  @Override
  public boolean isPotentiallyCompatible(final PsiType functionalInterfaceType) {
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
    if (interfaceMethod == null) return false;

    final MethodReferenceResolver resolver = new MethodReferenceResolver() {
      @Override
      protected PsiConflictResolver createResolver(PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult,
                                                   PsiMethod interfaceMethod,
                                                   MethodSignature signature) {
        return DuplicateConflictResolver.INSTANCE;
      }

      @Override
      protected PsiType getInterfaceType(PsiMethodReferenceExpression reference) {
        return functionalInterfaceType;
      }
    };

    final ResolveResult[] result = resolver.resolve(this, false);
    final PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult = PsiMethodReferenceUtil.getQualifierResolveResult(this);
    final int interfaceArity = interfaceMethod.getParameterList().getParametersCount();
    for (ResolveResult resolveResult : result) {
      final PsiElement element = resolveResult.getElement();
      if (element instanceof PsiMethod) {
        final boolean isStatic = ((PsiMethod)element).hasModifierProperty(PsiModifier.STATIC);
        if (qualifierResolveResult.isReferenceTypeQualified() && getReferenceNameElement() instanceof PsiIdentifier) {
          final int parametersCount = ((PsiMethod)element).getParameterList().getParametersCount();
          if (parametersCount == interfaceArity && isStatic) {
            return true;
          }
          if (parametersCount == interfaceArity - 1 && !isStatic) {
            return true;
          }
          if (((PsiMethod)element).isVarArgs()) return true;
        } else if (!isStatic) {
          return true;
        }
      } else if (element instanceof PsiClass) {
        return true;
      }
    }
    return false;
  }

  @Override
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
            final PsiReferenceExpression expression = (PsiReferenceExpression)qualifier;
            if (qualifierResolveResult.isReferenceTypeQualified()) {
              final PsiReferenceParameterList parameterList = expression.getParameterList();
              if (parameterList == null || parameterList.getTypeParameterElements().length == 0) {
                return null;
              }
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
  public boolean isConstructor() {
    final PsiElement element = getReferenceNameElement();
    return element instanceof PsiKeyword && PsiKeyword.NEW.equals(element.getText());
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

  private class MethodReferenceResolver implements ResolveCache.PolyVariantResolver<PsiMethodReferenceExpression> {
    @NotNull
    @Override
    public ResolveResult[] resolve(@NotNull final PsiMethodReferenceExpression reference, boolean incompleteCode) {
      final PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult = PsiMethodReferenceUtil.getQualifierResolveResult(reference);

      final PsiClass containingClass = qualifierResolveResult.getContainingClass();
      PsiSubstitutor substitutor = qualifierResolveResult.getSubstitutor();

      if (containingClass != null) {
        final PsiElement element = getReferenceNameElement();
        final boolean isConstructor = isConstructor();
        if (element instanceof PsiIdentifier || isConstructor) {
          if (isConstructor && (containingClass.isEnum() || containingClass.hasModifierProperty(PsiModifier.ABSTRACT))) {
            return JavaResolveResult.EMPTY_ARRAY;
          }
          final PsiType functionalInterfaceType = getInterfaceType(reference);
          final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
          final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
          final PsiSubstitutor functionalInterfaceSubstitutor = interfaceMethod != null ? LambdaUtil.getSubstitutor(interfaceMethod, resolveResult) : null;
          final MethodSignature signature = interfaceMethod != null ? interfaceMethod.getSignature(functionalInterfaceSubstitutor) : null;
          final PsiType interfaceMethodReturnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);
          if (isConstructor && interfaceMethod != null && containingClass.getConstructors().length == 0) {
            final PsiClassType returnType = composeReturnType(containingClass, substitutor);
            final InferenceSession session = new InferenceSession(containingClass.getTypeParameters(), substitutor, getManager(), null);
            if (!(session.isProperType(returnType) && session.isProperType(interfaceMethodReturnType))) {
              session.registerConstraints(returnType, interfaceMethodReturnType);
              substitutor = session.infer();
            }
            ClassCandidateInfo candidateInfo = null;
            final boolean isArray = containingClass == JavaPsiFacade.getElementFactory(getProject()).getArrayClass(PsiUtil.getLanguageLevel(containingClass));
            if (!isArray && (containingClass.getContainingClass() == null || !isLocatedInStaticContext(containingClass)) && signature.getParameterTypes().length == 0 ||
                isArray && arrayCreationSignature(signature)) {
              candidateInfo = new ClassCandidateInfo(containingClass, substitutor);
            }
            return candidateInfo == null ? JavaResolveResult.EMPTY_ARRAY : new JavaResolveResult[]{candidateInfo};
          }

          final PsiConflictResolver conflictResolver = createResolver(qualifierResolveResult, interfaceMethod, signature);
          final MethodCandidatesProcessor processor =
            new MethodCandidatesProcessor(reference, getContainingFile(), new PsiConflictResolver[] {conflictResolver}, new SmartList<CandidateInfo>()) {
              @Override
              protected MethodCandidateInfo createCandidateInfo(final PsiMethod method,
                                                                final PsiSubstitutor substitutor,
                                                                final boolean staticProblem,
                                                                final boolean accessible) {
                final PsiExpressionList argumentList = getArgumentList();
                final PsiType[] typeParameters = reference.getTypeParameters();
                return new MethodCandidateInfo(method, substitutor, !accessible, staticProblem, argumentList, myCurrentFileContext,
                                               argumentList != null ? argumentList.getExpressionTypes() : null, typeParameters.length > 0 ? typeParameters : null,
                                               getLanguageLevel()) {
                  @NotNull
                  @Override
                  public PsiSubstitutor inferTypeArguments(@NotNull ParameterTypeInferencePolicy policy, boolean includeReturnConstraint) {
                    return inferTypeArguments(false);
                  }

                  public PsiSubstitutor inferTypeArguments(boolean varargs) {
                    if (interfaceMethod == null) return substitutor;
                    final PsiSubstitutor qualifierResultSubstitutor = qualifierResolveResult.getSubstitutor();
                    final InferenceSession session = new InferenceSession(method.getTypeParameters(), substitutor, getManager(), reference);

                    //lift parameters from outer call
                    final Pair<PsiMethod,PsiSubstitutor> methodSubstitutorPair = MethodCandidateInfo.getCurrentMethod(reference.getParent());
                    if (methodSubstitutorPair != null) {
                      session.initBounds(methodSubstitutorPair.first.getTypeParameters());
                    }

                    final PsiParameter[] functionalMethodParameters = interfaceMethod.getParameterList().getParameters();
                    final PsiParameter[] parameters = method.getParameterList().getParameters();
                    final boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
                    if (parameters.length == functionalMethodParameters.length && !varargs || isStatic && varargs) {//static methods

                      if (method.isConstructor() && PsiUtil.isRawSubstitutor(containingClass, qualifierResultSubstitutor)) {
                        session.initBounds(containingClass.getTypeParameters());
                      }

                      for (int i = 0; i < functionalMethodParameters.length; i++) {
                        final PsiType pType = signature.getParameterTypes()[i];
                        session.addConstraint(new TypeCompatibilityConstraint(getParameterType(parameters, i, varargs), pType));
                      }
                    }
                    else if (parameters.length + 1 == functionalMethodParameters.length && !varargs || !isStatic && varargs && functionalMethodParameters.length > 0) { //instance methods
                      final PsiClass aClass = qualifierResolveResult.getContainingClass();
                      session.initBounds(aClass.getTypeParameters());

                      final PsiType pType = signature.getParameterTypes()[0];

                      PsiSubstitutor psiSubstitutor = qualifierResultSubstitutor;
                      // 15.28.1 If the ReferenceType is a raw type, and there exists a parameterization of this type, T, that is a supertype of P1,
                      // the type to search is the result of capture conversion (5.1.10) applied to T; 
                      // otherwise, the type to search is the same as the type of the first search. Again, the type arguments, if any, are given by the method reference.
                      if (PsiUtil.isRawSubstitutor(containingClass, qualifierResultSubstitutor)) {
                        final PsiClassType.ClassResolveResult pResult = PsiUtil.resolveGenericsClassInType(pType);
                        final PsiClass pClass = pResult.getElement();
                        final PsiSubstitutor receiverSubstitutor = pClass != null ? TypeConversionUtil.getClassSubstitutor(containingClass, pClass, pResult.getSubstitutor()) : null;
                        if (receiverSubstitutor != null) {
                          if (!method.hasTypeParameters() && signature.getParameterTypes().length == 1) return receiverSubstitutor;
                          psiSubstitutor = receiverSubstitutor;
                        }
                      }

                      final PsiType qType = JavaPsiFacade.getElementFactory(getProject()).createType(containingClass, psiSubstitutor);

                      session.addConstraint(new TypeCompatibilityConstraint(qType, pType));
                      
                      for (int i = 0; i < signature.getParameterTypes().length - 1; i++) {
                        final PsiType interfaceParamType = signature.getParameterTypes()[i + 1];
                        session.addConstraint(new TypeCompatibilityConstraint(getParameterType(parameters, i, varargs), interfaceParamType));
                      }
                    }

                    if (!session.repeatInferencePhases(false)) {
                      if (method.isVarArgs() && !varargs) {
                        return inferTypeArguments(true);
                      }
                      return substitutor;
                    }

                    if (interfaceMethodReturnType != PsiType.VOID) {
                      final PsiType returnType = method.isConstructor() ? composeReturnType(containingClass, substitutor) : method.getReturnType();
                      if (returnType != null) {
                        session.registerConstraints(returnType, interfaceMethodReturnType);
                      }
                    }
                    return session.infer(parameters, null, null);
                  }

                  private PsiType getParameterType(PsiParameter[] parameters, int i, boolean varargs) {
                    if (varargs && i >= parameters.length - 1) {
                      final PsiType type = parameters[parameters.length - 1].getType();
                      LOG.assertTrue(type instanceof PsiEllipsisType);
                      return ((PsiEllipsisType)type).getComponentType();
                    }
                    return parameters[i].getType();
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
          containingClass.processDeclarations(processor, state, reference, reference);
          return processor.getResult();
        }
      }
      return JavaResolveResult.EMPTY_ARRAY;
    }

    protected PsiType getInterfaceType(PsiMethodReferenceExpression reference) {
      PsiType functionalInterfaceType = null;
      final Map<PsiMethodReferenceExpression,PsiType> map = PsiMethodReferenceUtil.ourRefs.get();
      if (map != null) {
        functionalInterfaceType = FunctionalInterfaceParameterizationUtil.getGroundTargetType(map.get(reference));
      }

      if (functionalInterfaceType == null) {
        functionalInterfaceType = reference.getFunctionalInterfaceType();
      }

      return functionalInterfaceType;
    }

    protected PsiConflictResolver createResolver(PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult,
                                                             PsiMethod interfaceMethod,
                                                             MethodSignature signature) {
      return new MethodReferenceConflictResolver(qualifierResolveResult, signature, interfaceMethod != null && interfaceMethod.isVarArgs());
    }

    private PsiClassType composeReturnType(PsiClass containingClass, PsiSubstitutor substitutor) {
      final boolean isRawSubst = PsiUtil.isRawSubstitutor(containingClass, substitutor);
      return JavaPsiFacade.getElementFactory(containingClass.getProject())
        .createType(containingClass, isRawSubst ? PsiSubstitutor.EMPTY : substitutor);
    }

    private class MethodReferenceConflictResolver extends JavaMethodsConflictResolver {
      private final MethodSignature mySignature;
      private final PsiMethodReferenceUtil.QualifierResolveResult myQualifierResolveResult;
      private final boolean myFunctionalMethodVarArgs;

      private MethodReferenceConflictResolver(PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult,
                                              @Nullable MethodSignature signature, boolean varArgs) {
        super(PsiMethodReferenceExpressionImpl.this, signature != null ? signature.getParameterTypes() : PsiType.EMPTY_ARRAY, PsiUtil.getLanguageLevel(PsiMethodReferenceExpressionImpl.this));
        myQualifierResolveResult = qualifierResolveResult;
        myFunctionalMethodVarArgs = varArgs;
        mySignature = signature;
      }

      @Nullable
      @Override
      public CandidateInfo resolveConflict(@NotNull List<CandidateInfo> conflicts) {
        return resolveConflict(conflicts, false);
      }

      public CandidateInfo resolveConflict(@NotNull List<CandidateInfo> conflicts, boolean varargs) {
        if (mySignature == null) return null;

        checkSameSignatures(conflicts);
        checkAccessStaticLevels(conflicts, true);

        final PsiType[] parameterTypes = mySignature.getParameterTypes();
        boolean hasReceiver = PsiMethodReferenceUtil.hasReceiver(parameterTypes, myQualifierResolveResult,
                                                                 PsiMethodReferenceExpressionImpl.this);

        final List<CandidateInfo> firstCandidates = new ArrayList<CandidateInfo>();
        final List<CandidateInfo> secondCandidates = new ArrayList<CandidateInfo>();

        for (CandidateInfo conflict : conflicts) {
          if (!(conflict instanceof MethodCandidateInfo)) continue;
          final PsiMethod psiMethod = ((MethodCandidateInfo)conflict).getElement();
          if (psiMethod == null) continue;

          final PsiSubstitutor substitutor = conflict.getSubstitutor();
          final PsiType[] signatureParameterTypes2 = psiMethod.getSignature(substitutor).getParameterTypes();

          if (varargs && (!psiMethod.isVarArgs() || myFunctionalMethodVarArgs)) continue;

          if ((varargs || parameterTypes.length == signatureParameterTypes2.length) &&
              isCorrectAssignment(signatureParameterTypes2, parameterTypes, substitutor, varargs, 0)) {
            firstCandidates.add(conflict);
          }

          if (hasReceiver &&
              (varargs || parameterTypes.length == signatureParameterTypes2.length + 1) &&
              isCorrectAssignment(signatureParameterTypes2, parameterTypes, substitutor, varargs, 1)) {
            secondCandidates.add(conflict);
          }
        }

        if (myQualifierResolveResult.isReferenceTypeQualified() && getReferenceNameElement() instanceof PsiIdentifier) {
          //If the first search produces a static method, and no non-static method is applicable for the second search, then the result of the first search is the compile-time declaration.
          CandidateInfo candidateInfo = filterStaticCorrectCandidates(firstCandidates, secondCandidates, true);
          if (candidateInfo != null) {
            return candidateInfo;
          }

          //If the second search produces a non-static method, and no static method is applicable for the first search, then the result of the second search is the compile-time declaration.
          candidateInfo = filterStaticCorrectCandidates(secondCandidates, firstCandidates, false);
          if (candidateInfo != null) {
            return candidateInfo;
          }
        }

        checkSpecifics(firstCandidates,
                       varargs ? MethodCandidateInfo.ApplicabilityLevel.VARARGS : MethodCandidateInfo.ApplicabilityLevel.FIXED_ARITY, myLanguageLevel);

        checkSpecifics(secondCandidates, 
                       varargs ? MethodCandidateInfo.ApplicabilityLevel.VARARGS : MethodCandidateInfo.ApplicabilityLevel.FIXED_ARITY, myLanguageLevel);

        final int acceptedCount = firstCandidates.size() + secondCandidates.size();
        if (acceptedCount == 1) {
          return !firstCandidates.isEmpty() ? firstCandidates.get(0) : secondCandidates.get(0);
        }

        if (!varargs) {
          return resolveConflict(conflicts, true);
        }

        conflicts.clear();
        firstCandidates.addAll(secondCandidates);
        conflicts.addAll(firstCandidates);
        return null;
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

      /**
       * 15.13.1
       */
      private CandidateInfo filterStaticCorrectCandidates(List<CandidateInfo> firstCandidates,
                                                 List<CandidateInfo> secondCandidates, 
                                                 boolean shouldBeStatic) {
        if (firstCandidates.size() == 1) {
          final CandidateInfo candidateInfo = firstCandidates.get(0);
          final PsiElement element = candidateInfo.getElement();
          if (element instanceof PsiMethod) {
            final boolean isStatic = ((PsiMethod)element).hasModifierProperty(PsiModifier.STATIC);
            if (shouldBeStatic && isStatic || !shouldBeStatic && !isStatic) {
              for (CandidateInfo secondCandidate : secondCandidates) {
                final PsiElement psiElement = secondCandidate.getElement();
                if (psiElement instanceof PsiMethod) {
                  final boolean oppositeStatic = ((PsiMethod)psiElement).hasModifierProperty(PsiModifier.STATIC);
                  if (shouldBeStatic && !oppositeStatic || !shouldBeStatic && oppositeStatic) {
                    return null;
                  }
                }
              }
              return candidateInfo;
            }
          }
        }
        return null;
      }

      private boolean isCorrectAssignment(PsiType[] signatureParameterTypes2,
                                          PsiType[] parameterTypes,
                                          PsiSubstitutor substitutor,
                                          boolean varargs,
                                          int offset) {
        final int min = Math.min(signatureParameterTypes2.length, parameterTypes.length - offset);
        for (int i = 0; i < min; i++) {
          final PsiType type1 = substitutor.substitute(parameterTypes[i + offset]);
          final PsiType type2 = signatureParameterTypes2[i];
          if (varargs && i == signatureParameterTypes2.length - 1) {
            if (!TypeConversionUtil.isAssignable(type2, type1) && !TypeConversionUtil.isAssignable(((PsiArrayType)type2).getComponentType(), type1)) {
              return false;
            }
          }
          else if (!TypeConversionUtil.isAssignable(type2, type1)) {
            return false;
          }
        }
        return true;
      }
    }
  }

  @Override
  public boolean isAcceptable(PsiType left) {
    if (left instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)left).getConjuncts()) {
        if (isAcceptable(conjunct)) return true;
      }
      return false;
    }

    left = FunctionalInterfaceParameterizationUtil.getGroundTargetType(left);
    if (!isPotentiallyCompatible(left)) {
      return false;
    }

    final PsiElement argsList = PsiTreeUtil.getParentOfType(this, PsiExpressionList.class);
    if (MethodCandidateInfo.ourOverloadGuard.currentStack().contains(argsList)) {
      if (!isExact()) {
        return true;
      }
    }

     // A method reference is congruent with a function type if the following are true:
     //   The function type identifies a single compile-time declaration corresponding to the reference.
     //   One of the following is true:
     //      i)The return type of the function type is void.
     //     ii)The return type of the function type is R; 
     //        the result of applying capture conversion (5.1.10) to the return type of the invocation type (15.12.2.6) of the chosen declaration is R', 
     //        where R is the target type that may be used to infer R'; neither R nor R' is void; and R' is compatible with R in an assignment context.

    Map<PsiMethodReferenceExpression, PsiType> map = PsiMethodReferenceUtil.ourRefs.get();
    if (map == null) {
      map = new HashMap<PsiMethodReferenceExpression, PsiType>();
      PsiMethodReferenceUtil.ourRefs.set(map);
    }

    final JavaResolveResult result;
    try {
      if (map.put(this, left) != null) {
        return false;
      }
      result = advancedResolve(false);
    }
    finally {
      map.remove(this);
    }

    final PsiElement resolve = result.getElement();
    if (resolve == null) {
      return false;
    }

    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(left);
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
    if (interfaceMethod != null) {
      final PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(left);

      LOG.assertTrue(interfaceReturnType != null);

      if (interfaceReturnType == PsiType.VOID) {
        return true;
      }

      final PsiSubstitutor subst = result.getSubstitutor();

      PsiType methodReturnType = null;
      PsiClass containingClass = null;
      if (resolve instanceof PsiMethod) {
        containingClass = ((PsiMethod)resolve).getContainingClass();

        PsiType returnType = PsiTypesUtil.patchMethodGetClassReturnType(this, this, (PsiMethod)resolve, null, PsiUtil.getLanguageLevel(this));

        if (returnType == null) {
          returnType = ((PsiMethod)resolve).getReturnType();
        }

        if (returnType == PsiType.VOID) {
          return false;
        }

        methodReturnType = subst.substitute(returnType);
      }
      else if (resolve instanceof PsiClass) {
        if (resolve == JavaPsiFacade.getElementFactory(resolve.getProject()).getArrayClass(PsiUtil.getLanguageLevel(resolve))) {
          final PsiTypeParameter[] typeParameters = ((PsiClass)resolve).getTypeParameters();
          if (typeParameters.length == 1) {
            final PsiType arrayComponentType = subst.substitute(typeParameters[0]);
            if (arrayComponentType == null) {
              return false;
            }
            methodReturnType = arrayComponentType.createArrayType();
          }
        }
        containingClass = (PsiClass)resolve;
      }

      if (methodReturnType == null) {
        if (containingClass == null) {
          return false;
        }
        methodReturnType = JavaPsiFacade.getElementFactory(getProject()).createType(containingClass, subst);
      }

      return TypeConversionUtil.isAssignable(interfaceReturnType, methodReturnType, false);
    }
    return false;
  }
}
