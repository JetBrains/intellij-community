/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.conflictResolvers.DuplicateConflictResolver;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PsiMethodReferenceExpressionImpl extends PsiReferenceExpressionBase implements PsiMethodReferenceExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiMethodReferenceExpressionImpl");
  private static final MethodReferenceResolver RESOLVER = new MethodReferenceResolver();

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

  @Override
  public boolean isPotentiallyCompatible(final PsiType functionalInterfaceType) {
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
    if (interfaceMethod == null) return false;

    final MethodReferenceResolver resolver = new MethodReferenceResolver() {
      @Override
      protected PsiConflictResolver createResolver(PsiMethodReferenceExpressionImpl referenceExpression,
                                                   PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult,
                                                   PsiMethod interfaceMethod,
                                                   MethodSignature signature) {
        return DuplicateConflictResolver.INSTANCE;
      }

      @Override
      protected PsiType getInterfaceType(PsiMethodReferenceExpression reference) {
        return functionalInterfaceType;
      }
    };

    final ResolveResult[] result = resolver.resolve(this, getContainingFile(), false);
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
        final String identifierName = element.getText();
        final List<PsiMethod> result = new ArrayList<PsiMethod>();
        for (HierarchicalMethodSignature signature : containingClass.getVisibleSignatures()) {
          if (identifierName.equals(signature.getName())) {
            result.add(signature.getMethod());
          }
        }
        methods = result.toArray(new PsiMethod[result.size()]);
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
      LOG.error("Forbidden null qualifier");
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
    final Map<PsiMethodReferenceExpression, PsiType> map = PsiMethodReferenceUtil.ourRefs.get();
    if (map != null && map.containsKey(this)) {
      return RESOLVER.resolve(this, file, incompleteCode);
    }
    ResolveResult[] results = ResolveCache.getInstance(getProject()).resolveWithCaching(this, RESOLVER, true, incompleteCode, file);
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

  @Override
  public boolean isAcceptable(PsiType left) {
    if (left instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)left).getConjuncts()) {
        if (isAcceptable(conjunct)) return true;
      }
      return false;
    }

    final PsiElement argsList = PsiTreeUtil.getParentOfType(this, PsiExpressionList.class);
    final boolean isExact = isExact();
    if (MethodCandidateInfo.ourOverloadGuard.currentStack().contains(argsList) && isExact) {
      final MethodCandidateInfo.CurrentCandidateProperties candidateProperties = MethodCandidateInfo.getCurrentMethod(argsList);
      if (candidateProperties != null && !InferenceSession.isPertinentToApplicability(this, candidateProperties.getMethod())) {
        return true;
      }
    }

    left = FunctionalInterfaceParameterizationUtil.getGroundTargetType(left);
    if (!isPotentiallyCompatible(left)) {
      return false;
    }

    if (MethodCandidateInfo.ourOverloadGuard.currentStack().contains(argsList)) {
      if (!isExact) {
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

    Map<PsiMethodReferenceExpression, PsiType> map = PsiMethodReferenceUtil.getFunctionalTypeMap();
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

      if (interfaceReturnType == PsiType.VOID || interfaceReturnType == null) {
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

  @Nullable
  @Override
  public Icon getIcon(int flags) {
    return AllIcons.Nodes.AnonymousClass;
  }
}
