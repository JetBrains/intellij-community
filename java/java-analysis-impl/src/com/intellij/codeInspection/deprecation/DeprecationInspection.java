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

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.DeprecationUtil;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author max
 */
public class DeprecationInspection extends BaseJavaBatchLocalInspectionTool {
  public static final String SHORT_NAME = DeprecationUtil.DEPRECATION_SHORT_NAME;
  public static final String ID = DeprecationUtil.DEPRECATION_ID;
  public static final String DISPLAY_NAME = DeprecationUtil.DEPRECATION_DISPLAY_NAME;
  public static final String IGNORE_METHODS_OF_DEPRECATED_NAME = "IGNORE_METHODS_OF_DEPRECATED";

  public boolean IGNORE_INSIDE_DEPRECATED = true;
  public boolean IGNORE_ABSTRACT_DEPRECATED_OVERRIDES = true;
  public boolean IGNORE_IMPORT_STATEMENTS = true;
  public boolean IGNORE_METHODS_OF_DEPRECATED = true;

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new DeprecationElementVisitor(holder, IGNORE_INSIDE_DEPRECATED, IGNORE_ABSTRACT_DEPRECATED_OVERRIDES,
                                         IGNORE_IMPORT_STATEMENTS, IGNORE_METHODS_OF_DEPRECATED);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  @NotNull
  @SuppressWarnings("PatternOverriddenByNonAnnotatedMethod")
  public String getID() {
    return ID;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox("Ignore inside deprecated members", "IGNORE_INSIDE_DEPRECATED");
    panel.addCheckbox("Ignore inside non-static imports", "IGNORE_IMPORT_STATEMENTS");
    panel.addCheckbox("<html>Ignore overrides of deprecated abstract methods from non-deprecated supers</html>", "IGNORE_ABSTRACT_DEPRECATED_OVERRIDES");
    panel.addCheckbox("Ignore members of deprecated classes", IGNORE_METHODS_OF_DEPRECATED_NAME);
    return panel;
  }

  private static class DeprecationElementVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;
    private final boolean myIgnoreInsideDeprecated;
    private final boolean myIgnoreAbstractDeprecatedOverrides;
    private final boolean myIgnoreImportStatements;
    private final boolean myIgnoreMethodsOfDeprecated;

    private DeprecationElementVisitor(ProblemsHolder holder,
                                      boolean ignoreInsideDeprecated,
                                      boolean ignoreAbstractDeprecatedOverrides,
                                      boolean ignoreImportStatements,
                                      boolean ignoreMethodsOfDeprecated) {
      myHolder = holder;
      myIgnoreInsideDeprecated = ignoreInsideDeprecated;
      myIgnoreAbstractDeprecatedOverrides = ignoreAbstractDeprecatedOverrides;
      myIgnoreImportStatements = ignoreImportStatements;
      myIgnoreMethodsOfDeprecated = ignoreMethodsOfDeprecated;
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      PsiElement resolved = reference.advancedResolve(true).getElement();
      PsiElement refName = reference.getReferenceNameElement();
      checkDeprecated(resolved, refName, null, myIgnoreInsideDeprecated, myIgnoreImportStatements, myIgnoreMethodsOfDeprecated, myHolder);
    }

    @Override
    public void visitImportStaticStatement(PsiImportStaticStatement statement) {
      PsiJavaCodeReferenceElement importReference = statement.getImportReference();
      if (importReference != null) {
        PsiElement refName = importReference.getReferenceNameElement();
        checkDeprecated(importReference.resolve(), refName, null, myIgnoreInsideDeprecated, false, true, myHolder);
      }
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      PsiClass aClass = null;
      PsiType type = expression.getType();
      if (type instanceof PsiClassType) {
        aClass = ((PsiClassType)type).resolveGenerics().getElement();
        if (aClass instanceof PsiAnonymousClass) {
          type = ((PsiAnonymousClass)aClass).getBaseClassType();
          aClass = ((PsiClassType)type).resolveGenerics().getElement();
        }
      }
      if (aClass == null) return;
      PsiExpressionList list = expression.getArgumentList();
      PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(expression.getProject()).getResolveHelper();
      if (list != null && aClass.getConstructors().length > 0) {
        JavaResolveResult[] results = resolveHelper.multiResolveConstructor((PsiClassType)type, list, list);
        MethodCandidateInfo result = null;
        if (results.length == 1) result = (MethodCandidateInfo)results[0];

        PsiMethod constructor = result == null ? null : result.getElement();
        PsiJavaCodeReferenceElement ref;
        if (constructor != null && (ref = expression.getClassOrAnonymousClassReference()) != null) {
          if (expression.getClassReference() == null && constructor.getParameterList().getParametersCount() == 0) return;
          checkDeprecated(constructor, ref, null, myIgnoreInsideDeprecated, myIgnoreImportStatements, true, myHolder);
        }
      }
    }

    @Override
    public void visitMethod(PsiMethod method) {
      MethodSignatureBackedByPsiMethod methodSignature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY);
      if (!method.isConstructor()) {
        List<MethodSignatureBackedByPsiMethod> superMethodSignatures = method.findSuperMethodSignaturesIncludingStatic(true);
        checkMethodOverridesDeprecated(methodSignature, superMethodSignatures, myIgnoreAbstractDeprecatedOverrides, myHolder);
      }
      else {
        checkImplicitCallToSuper(method);
      }
    }

    private void checkImplicitCallToSuper(PsiMethod method) {
      final PsiClass containingClass = method.getContainingClass();
      assert containingClass != null;
      final PsiClass superClass = containingClass.getSuperClass();
      if (hasDefaultDeprecatedConstructor(superClass)) {
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
      ProblemHighlightType type = asDeprecated ? ProblemHighlightType.LIKE_DEPRECATED : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
      myHolder.registerProblem(nameIdentifier, "Default constructor in " + superClass.getQualifiedName() + " is deprecated", type);
    }

    @Override
    public void visitClass(PsiClass aClass) {
      if (aClass instanceof PsiTypeParameter) return;
      final PsiMethod[] currentConstructors = aClass.getConstructors();
      if (currentConstructors.length == 0) {
        final PsiClass superClass = aClass.getSuperClass();
        if (hasDefaultDeprecatedConstructor(superClass)) {
          final boolean isAnonymous = aClass instanceof PsiAnonymousClass;
          if (isAnonymous) {
            final PsiExpressionList argumentList = ((PsiAnonymousClass)aClass).getArgumentList();
            if (argumentList != null && argumentList.getExpressions().length > 0) return;
          }
          PsiElement identifier = isAnonymous ? ((PsiAnonymousClass)aClass).getBaseClassReference() : aClass.getNameIdentifier();
          registerDefaultConstructorProblem(superClass, identifier, isAnonymous);
        }
      }
    }

    @Override
    public void visitRequiresStatement(PsiRequiresStatement statement) {
      PsiJavaModuleReferenceElement refElement = statement.getReferenceElement();
      if (refElement != null) {
        PsiPolyVariantReference ref = refElement.getReference();
        PsiElement target = ref != null ? ref.resolve() : null;
        if (target instanceof PsiJavaModule && PsiImplUtil.isDeprecatedByAnnotation((PsiJavaModule)target)) {
          String message = JavaErrorMessages.message("deprecated.symbol", HighlightMessageUtil.getSymbolName(target));
          myHolder.registerProblem(refElement, message, ProblemHighlightType.LIKE_DEPRECATED);
        }
      }
    }
  }

  private static boolean hasDefaultDeprecatedConstructor(PsiClass superClass) {
    if (superClass != null) {
      final PsiMethod[] constructors = superClass.getConstructors();
      for (PsiMethod constructor : constructors) {
        if (constructor.getParameterList().getParametersCount() == 0 && constructor.isDeprecated()) {
          return true;
        }
      }
    }
    return false;
  }

  private static void checkMethodOverridesDeprecated(MethodSignatureBackedByPsiMethod methodSignature,
                                                     List<MethodSignatureBackedByPsiMethod> superMethodSignatures,
                                                     boolean ignoreAbstractDeprecatedOverrides,
                                                     ProblemsHolder holder) {
    PsiMethod method = methodSignature.getMethod();
    PsiElement methodName = method.getNameIdentifier();
    if (methodName == null) return;
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      PsiClass aClass = superMethod.getContainingClass();
      if (aClass == null) continue;
      // do not show deprecated warning for class implementing deprecated methods
      if (ignoreAbstractDeprecatedOverrides && !aClass.isDeprecated() && superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) continue;
      if (superMethod.isDeprecated()) {
        String description = JavaErrorMessages.message("overrides.deprecated.method", HighlightMessageUtil.getSymbolName(aClass));
        holder.registerProblem(methodName, description, ProblemHighlightType.LIKE_DEPRECATED);
      }
    }
  }

  public static void checkDeprecated(PsiElement refElement,
                                     PsiElement elementToHighlight,
                                     @Nullable TextRange rangeInElement,
                                     ProblemsHolder holder) {
    checkDeprecated(refElement, elementToHighlight, rangeInElement, false, false, true, holder);
  }

  private static void checkDeprecated(PsiElement refElement,
                                      PsiElement elementToHighlight,
                                      @Nullable TextRange rangeInElement,
                                      boolean ignoreInsideDeprecated,
                                      boolean ignoreImportStatements,
                                      boolean ignoreMethodsOfDeprecated,
                                      ProblemsHolder holder) {
    if (!(refElement instanceof PsiDocCommentOwner)) return;

    if (!((PsiDocCommentOwner)refElement).isDeprecated()) {
      if (!ignoreMethodsOfDeprecated) {
        checkDeprecated(((PsiDocCommentOwner)refElement).getContainingClass(), elementToHighlight, rangeInElement,
                        ignoreInsideDeprecated, ignoreImportStatements, false, holder);
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

    String description = JavaErrorMessages.message("deprecated.symbol", HighlightMessageUtil.getSymbolName(refElement));
    holder.registerProblem(elementToHighlight, description, ProblemHighlightType.LIKE_DEPRECATED, rangeInElement);
  }
}