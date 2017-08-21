/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.deprecation;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

abstract class DeprecationInspectionBase extends BaseJavaBatchLocalInspectionTool {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  protected static class DeprecationElementVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;
    private final boolean myIgnoreInsideDeprecated;
    private final boolean myIgnoreAbstractDeprecatedOverrides;
    private final boolean myIgnoreImportStatements;
    private final boolean myIgnoreMethodsOfDeprecated;
    private final boolean myForRemoval;

    DeprecationElementVisitor(@NotNull ProblemsHolder holder,
                              boolean ignoreInsideDeprecated,
                              boolean ignoreAbstractDeprecatedOverrides,
                              boolean ignoreImportStatements,
                              boolean ignoreMethodsOfDeprecated,
                              boolean forRemoval) {
      myHolder = holder;
      myIgnoreInsideDeprecated = ignoreInsideDeprecated;
      myIgnoreAbstractDeprecatedOverrides = ignoreAbstractDeprecatedOverrides;
      myIgnoreImportStatements = ignoreImportStatements;
      myIgnoreMethodsOfDeprecated = ignoreMethodsOfDeprecated;
      myForRemoval = forRemoval;
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      JavaResolveResult result = reference.advancedResolve(true);
      PsiElement resolved = result.getElement();
      checkDeprecated(resolved, reference.getReferenceNameElement(), null, myIgnoreInsideDeprecated, myIgnoreImportStatements,
                      myIgnoreMethodsOfDeprecated, myHolder, myForRemoval);
    }

    @Override
    public void visitImportStaticStatement(PsiImportStaticStatement statement) {
      PsiFile file = statement.getContainingFile();
      if (file instanceof PsiJavaFile && ((PsiJavaFile)file).getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_9)) return;
      final PsiJavaCodeReferenceElement importReference = statement.getImportReference();
      if (importReference != null) {
        PsiElement resolved = importReference.resolve();
        checkDeprecated(resolved, importReference.getReferenceNameElement(), null, myIgnoreInsideDeprecated,
                        false, true, myHolder, myForRemoval);
      }
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      PsiType type = expression.getType();
      PsiExpressionList list = expression.getArgumentList();
      if (!(type instanceof PsiClassType)) return;
      PsiClassType.ClassResolveResult typeResult = ((PsiClassType)type).resolveGenerics();
      PsiClass aClass = typeResult.getElement();
      if (aClass == null) return;
      if (aClass instanceof PsiAnonymousClass) {
        type = ((PsiAnonymousClass)aClass).getBaseClassType();
        typeResult = ((PsiClassType)type).resolveGenerics();
        aClass = typeResult.getElement();
        if (aClass == null) return;
      }
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(expression.getProject()).getResolveHelper();
      final PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length > 0 && list != null) {
        JavaResolveResult[] results = resolveHelper.multiResolveConstructor((PsiClassType)type, list, list);
        MethodCandidateInfo result = null;
        if (results.length == 1) result = (MethodCandidateInfo)results[0];

        PsiMethod constructor = result == null ? null : result.getElement();
        if (constructor != null && expression.getClassOrAnonymousClassReference() != null) {
          if (expression.getClassReference() == null && constructor.getParameterList().getParametersCount() == 0) return;
          checkDeprecated(constructor, expression.getClassOrAnonymousClassReference(), null, myIgnoreInsideDeprecated,
                          myIgnoreImportStatements, true, myHolder, myForRemoval);
        }
      }
    }

    @Override
    public void visitMethod(PsiMethod method) {
      MethodSignatureBackedByPsiMethod methodSignature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY);
      if (!method.isConstructor()) {
        List<MethodSignatureBackedByPsiMethod> superMethodSignatures = method.findSuperMethodSignaturesIncludingStatic(true);
        checkMethodOverridesDeprecated(methodSignature, superMethodSignatures, myIgnoreAbstractDeprecatedOverrides, myHolder, myForRemoval);
      }
      else {
        checkImplicitCallToSuper(method);
      }
    }

    private void checkImplicitCallToSuper(PsiMethod method) {
      final PsiClass containingClass = method.getContainingClass();
      assert containingClass != null;
      final PsiClass superClass = containingClass.getSuperClass();
      if (hasDefaultDeprecatedConstructor(superClass, myForRemoval)) {
        if (superClass instanceof PsiAnonymousClass) {
          final PsiExpressionList argumentList = ((PsiAnonymousClass)superClass).getArgumentList();
          if (argumentList != null && argumentList.getExpressions().length > 0) return;
        }
        final PsiCodeBlock body = method.getBody();
        if (body != null) {
          final PsiStatement[] statements = body.getStatements();
          if (statements.length == 0 || !JavaHighlightUtil.isSuperOrThisCall(statements[0], true, true)) {
            registerDefaultConstructorProblem(superClass, method.getNameIdentifier(), false);
          }
        }
      }
    }

    private void registerDefaultConstructorProblem(PsiClass superClass, PsiElement nameIdentifier, boolean asDeprecated) {
      String description =
        JavaErrorMessages.message(myForRemoval ? "marked.for.removal.default.constructor" : "deprecated.default.constructor",
                                  superClass.getQualifiedName());
      ProblemHighlightType type =
        asDeprecated && !myForRemoval ? ProblemHighlightType.LIKE_DEPRECATED : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
      myHolder.registerProblem(nameIdentifier, description, type);
    }

    @Override
    public void visitClass(PsiClass aClass) {
      if (aClass instanceof PsiTypeParameter) return;
      final PsiMethod[] currentConstructors = aClass.getConstructors();
      if (currentConstructors.length == 0) {
        final PsiClass superClass = aClass.getSuperClass();
        if (hasDefaultDeprecatedConstructor(superClass, myForRemoval)) {
          final boolean isAnonymous = aClass instanceof PsiAnonymousClass;
          if (isAnonymous) {
            final PsiExpressionList argumentList = ((PsiAnonymousClass)aClass).getArgumentList();
            if (argumentList != null && argumentList.getExpressions().length > 0) return;
          }
          registerDefaultConstructorProblem(superClass,
                                            isAnonymous ? ((PsiAnonymousClass)aClass).getBaseClassReference() : aClass.getNameIdentifier(),
                                            isAnonymous);
        }
      }
    }

    @Override
    public void visitRequiresStatement(PsiRequiresStatement statement) {
      PsiJavaModuleReferenceElement refElement = statement.getReferenceElement();
      if (refElement != null) {
        PsiPolyVariantReference ref = refElement.getReference();
        PsiElement target = ref != null ? ref.resolve() : null;
        if (target instanceof PsiJavaModule &&
            isMarkedForRemoval((PsiJavaModule)target, myForRemoval) &&
            PsiImplUtil.isDeprecatedByAnnotation((PsiJavaModule)target)) {
          String description = JavaErrorMessages.message(myForRemoval ? "marked.for.removal.symbol" : "deprecated.symbol",
                                                         HighlightMessageUtil.getSymbolName(target));
          ProblemHighlightType type = myForRemoval ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING : ProblemHighlightType.LIKE_DEPRECATED;
          myHolder.registerProblem(refElement, description, type);
        }
      }
    }
  }

  private static boolean hasDefaultDeprecatedConstructor(PsiClass superClass, boolean forRemoval) {
    return superClass != null && Arrays.stream(superClass.getConstructors())
      .anyMatch(constructor -> constructor.getParameterList().getParametersCount() == 0 &&
                               constructor.isDeprecated() &&
                               isMarkedForRemoval(constructor, forRemoval));
  }

  //@top
  static void checkMethodOverridesDeprecated(MethodSignatureBackedByPsiMethod methodSignature,
                                             List<MethodSignatureBackedByPsiMethod> superMethodSignatures,
                                             boolean ignoreAbstractDeprecatedOverrides, ProblemsHolder holder, boolean forRemoval) {
    PsiMethod method = methodSignature.getMethod();
    PsiElement methodName = method.getNameIdentifier();
    if (methodName == null) return;
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      PsiClass aClass = superMethod.getContainingClass();
      if (aClass == null) continue;
      // do not show deprecated warning for class implementing deprecated methods
      if (ignoreAbstractDeprecatedOverrides && !aClass.isDeprecated() && superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) continue;
      if (superMethod.isDeprecated() && isMarkedForRemoval(superMethod, forRemoval)) {
        String description = JavaErrorMessages.message(forRemoval ? "overrides.marked.for.removal.method" : "overrides.deprecated.method",
                                                       HighlightMessageUtil.getSymbolName(aClass, PsiSubstitutor.EMPTY));
        ProblemHighlightType type = forRemoval ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING : ProblemHighlightType.LIKE_DEPRECATED;
        holder.registerProblem(methodName, description, type);
      }
    }
  }

  static void checkDeprecated(PsiElement refElement,
                              PsiElement elementToHighlight,
                              @Nullable TextRange rangeInElement,
                              boolean ignoreInsideDeprecated,
                              boolean ignoreImportStatements,
                              boolean ignoreMethodsOfDeprecated,
                              ProblemsHolder holder,
                              boolean forRemoval) {
    if (!(refElement instanceof PsiDocCommentOwner) || !isMarkedForRemoval((PsiDocCommentOwner)refElement, forRemoval)) {
      return;
    }

    if (!((PsiDocCommentOwner)refElement).isDeprecated()) {
      if (!ignoreMethodsOfDeprecated) {
        checkDeprecated(((PsiDocCommentOwner)refElement).getContainingClass(), elementToHighlight, rangeInElement,
                        ignoreInsideDeprecated, ignoreImportStatements, false, holder, forRemoval);
      }
      return;
    }

    if (ignoreInsideDeprecated) {
      PsiElement parent = elementToHighlight;
      while ((parent = PsiTreeUtil.getParentOfType(parent, PsiDocCommentOwner.class, true)) != null) {
        if (((PsiDocCommentOwner)parent).isDeprecated()) return;
      }
    }

    if (ignoreImportStatements && PsiTreeUtil.getParentOfType(elementToHighlight, PsiImportStatementBase.class) != null) {
      return;
    }

    String description = JavaErrorMessages.message(forRemoval ? "marked.for.removal.symbol" : "deprecated.symbol",
                                                   HighlightMessageUtil.getSymbolName(refElement, PsiSubstitutor.EMPTY));

    ProblemHighlightType type = forRemoval ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING : ProblemHighlightType.LIKE_DEPRECATED;
    holder.registerProblem(elementToHighlight, description, type, rangeInElement);
  }

  private static boolean isMarkedForRemoval(PsiModifierListOwner superMethod, boolean forRemoval) {
    return isMarkedForRemoval(superMethod) == forRemoval;
  }

  private static boolean isMarkedForRemoval(@Nullable PsiModifierListOwner element) {
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(element, CommonClassNames.JAVA_LANG_DEPRECATED);
    if (annotation == null) {
      return false;
    }
    PsiAnnotationMemberValue value = annotation.findAttributeValue("forRemoval");
    Object result = null;
    if (value instanceof PsiLiteral) {
      result = ((PsiLiteral)value).getValue();
    }
    else if (value instanceof PsiExpression) {
      result = JavaConstantExpressionEvaluator.computeConstantExpression((PsiExpression)value, false);
    }
    return result instanceof Boolean && ((Boolean)result);
  }
}
