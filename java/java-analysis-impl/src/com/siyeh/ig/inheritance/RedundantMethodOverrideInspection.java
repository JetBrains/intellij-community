// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.inheritance;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.migration.TryWithIdenticalCatchesInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TrackingEquivalenceChecker;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class RedundantMethodOverrideInspection extends BaseInspection {

  public boolean checkLibraryMethods = true;
  public boolean ignoreDelegates = true;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    boolean delegatesToSuperMethod = (boolean)infos[0];
    return delegatesToSuperMethod
           ? InspectionGadgetsBundle.message("redundant.method.override.delegates.to.super.problem.descriptor")
           : InspectionGadgetsBundle.message("redundant.method.override.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("checkLibraryMethods", InspectionGadgetsBundle.message("redundant.method.override.option.check.library.methods")),
      checkbox("ignoreDelegates", InspectionGadgetsBundle.message("redundant.method.override.option.ignore.delegates"))
    );
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    if (infos.length > 0 && infos[0] instanceof Boolean isDelegate && isDelegate) {
      return new LocalQuickFix[] { new RedundantMethodOverrideFix() };
    }
    return new LocalQuickFix[] { new RedundantMethodOverrideFix(), new ReplaceWithSuperDelegateFix() };
  }

  private static class ReplaceWithSuperDelegateFix extends PsiUpdateModCommandQuickFix {

    private static @Nullable PsiClassType findRequiredSuperQualifier(@Nullable PsiClass contextClass, PsiMethod methodToCall) {
      if (contextClass == null) return null;
      PsiClass superClass = methodToCall.getContainingClass();
      if (superClass == null || !superClass.isInterface()) return null;
      if (contextClass instanceof PsiAnonymousClass anonymousClass) {
        PsiClass baseClass = anonymousClass.getBaseClassType().resolve();
        return baseClass != null && baseClass.isInterface() ? anonymousClass.getBaseClassType() : null;
      }
      PsiClassType superType = PsiTypesUtil.getClassType(superClass);
      if (!contextClass.isInterface() &&
          ContainerUtil.exists(contextClass.getExtendsListTypes(), type -> TypeConversionUtil.isAssignable(superType, type))) {
        return null;
      }
      PsiClassType[] types = contextClass.isInterface() ? contextClass.getExtendsListTypes() : contextClass.getImplementsListTypes();
      return ContainerUtil.findLast(Arrays.asList(types), type -> TypeConversionUtil.isAssignable(superType, type));
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("redundant.method.override.delegate.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement methodNameIdentifier, @NotNull ModPsiUpdater updater) {
      if (!(methodNameIdentifier.getParent() instanceof PsiMethod method)) return;
      PsiMethod superMethod = findSuperMethod(method);
      if (superMethod == null) return;
      PsiClassType requiredQualifier = findRequiredSuperQualifier(method.getContainingClass(), superMethod);
      String qualifier = requiredQualifier != null ? requiredQualifier.rawType().getCanonicalText() + ".super." : "super.";
      String parameters = StringUtil.join(superMethod.getParameterList().getParameters(), PsiParameter::getName, ",");
      String call = qualifier + method.getName() + "(" + parameters + ");";
      if (!PsiTypes.voidType().equals(method.getReturnType())) {
        call = "return " + call;
      }
      PsiSubstitutor substitutor = getSuperSubstitutor(method, superMethod);
      if (substitutor == null) return;
      PsiParameterList parameterList = (PsiParameterList) superMethod.getParameterList().copy();
      for (PsiParameter parameter: parameterList.getParameters()) {
        PsiTypeElement newType = PsiElementFactory.getInstance(project).createTypeElement(substitutor.substitute(parameter.getType()));
        PsiTypeElement oldType = parameter.getTypeElement();
        assert oldType != null;
        oldType.replace(newType);
      }
      for (PsiParameter parameter: method.getParameterList().getParameters()) {
        PsiTypeElement newType = PsiElementFactory.getInstance(project).createTypeElement(substitutor.substitute(parameter.getType()));
        PsiTypeElement oldType = parameter.getTypeElement();
        assert oldType != null;
        oldType.replace(newType);
      }
      method.getParameterList().replace(parameterList);
      PsiCodeBlock methodBody = method.getBody();
      if (methodBody != null) {
        for (PsiStatement element : methodBody.getStatements()) {
          element.delete();
        }
        PsiStatement statement = PsiElementFactory.getInstance(project).createStatementFromText(call, methodBody);
        methodBody.add(statement);
      }
    }

    private static @Nullable PsiSubstitutor getSuperSubstitutor(PsiMethod method, PsiMethod superMethod) {
      PsiClass contextClass = method.getContainingClass();
      PsiClass superClass = superMethod.getContainingClass();
      if (contextClass == null || superClass == null) return null;
      PsiSubstitutor classSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, contextClass, PsiSubstitutor.EMPTY);
      MethodSignature contextSignature = method.getSignature(PsiSubstitutor.EMPTY);
      MethodSignature superSignature = superMethod.getSignature(classSubstitutor);
      return MethodSignatureUtil.getSuperMethodSignatureSubstitutor(contextSignature, superSignature);
    }
  }

  private static class RedundantMethodOverrideFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("redundant.method.override.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement methodNameIdentifier, @NotNull ModPsiUpdater updater) {
      final PsiElement method = methodNameIdentifier.getParent();
      assert method != null;
      method.delete();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RedundantMethodOverrideVisitor();
  }

  private static @Nullable PsiMethod findSuperMethod(@NotNull PsiMethod method) {
    final PsiMethod[] superMethods = method.findSuperMethods();
    if (superMethods.length == 1) {
      return superMethods[0];
    }
    else {
      return StreamEx.of(superMethods).findFirst(candidate -> isNotInterface(candidate.getContainingClass())).orElse(null);
    }
  }

  private static boolean isNotInterface(@Nullable PsiClass psiClass) {
    return psiClass != null && !psiClass.isInterface();
  }

  private class RedundantMethodOverrideVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (method.isConstructor()) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null || method.getNameIdentifier() == null) {
        return;
      }
      PsiMethod superMethod = findSuperMethod(method);
      if (superMethod == null ||
          !AbstractMethodOverridesAbstractMethodInspection.methodsHaveSameAnnotationsAndModifiers(method, superMethod) ||
          !AbstractMethodOverridesAbstractMethodInspection.methodsHaveSameReturnTypes(method, superMethod) ||
          !AbstractMethodOverridesAbstractMethodInspection.haveSameExceptionSignatures(method, superMethod) ||
          (method.getDocComment() != null && !AbstractMethodOverridesAbstractMethodInspection.haveSameJavaDoc(method, superMethod)) ||
          method.isVarArgs() != superMethod.isVarArgs()) {
        return;
      }
      if (isSuperCallWithSameArguments(body, method, superMethod)) {
        if (ignoreDelegates) return;
        registerMethodError(method, Boolean.TRUE);
        return;
      }
      if (checkLibraryMethods && superMethod instanceof PsiCompiledElement) {
        final PsiElement navigationElement = superMethod.getNavigationElement();
        if (!(navigationElement instanceof PsiMethod)) {
          return;
        }
        superMethod = (PsiMethod)navigationElement;
      }
      if (superMethod.hasModifierProperty(PsiModifier.DEFAULT) && !HighlightingFeature.EXTENSION_METHODS.isAvailable(method)) {
        return;
      }
      final PsiCodeBlock superBody = superMethod.getBody();
      final PsiMethod finalSuperMethod = superMethod;
      final TrackingEquivalenceChecker checker = new TrackingEquivalenceChecker() {
        @Override
        protected boolean equivalentDeclarations(PsiElement element1, PsiElement element2) {
          if (super.equivalentDeclarations(element1, element2)) {
            return true;
          }
          return checkLibraryMethods && element1.getNavigationElement().equals(element2.getNavigationElement());
        }

        @Override
        protected PsiClass getQualifierTarget(PsiReferenceExpression ref) {
          PsiClass target = super.getQualifierTarget(ref);
          if (target == method.getContainingClass() && target == ClassUtils.getContainingClass(ref)) {
            return finalSuperMethod.getContainingClass();
          }
          return target;
        }

        @Override
        protected @NotNull Match thisExpressionsMatch(@NotNull PsiThisExpression thisExpression1,
                                                      @NotNull PsiThisExpression thisExpression2) {
          final PsiClass containingClass1 = PsiUtil.resolveClassInClassTypeOnly(thisExpression1.getType());
          final PsiClass containingClass2 = PsiUtil.resolveClassInClassTypeOnly(thisExpression2.getType());
          if (containingClass1 == finalSuperMethod.getContainingClass()) {
            if (containingClass2 == method.getContainingClass()) {
              return EXACT_MATCH;
            }
          }
          else if (containingClass1 == containingClass2) {
            return EXACT_MATCH;
          }
          return EXACT_MISMATCH;
        }
      };
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final PsiParameter[] superParameters = superMethod.getParameterList().getParameters();
      for (int i = 0; i < parameters.length; i++) {
        checker.markDeclarationsAsEquivalent(parameters[i], superParameters[i]);
      }
      checker.markDeclarationsAsEquivalent(method, superMethod);
      if (checker.codeBlocksAreEquivalent(body, superBody) && haveTheSameComments(method, superMethod)) {
          registerMethodError(method, Boolean.FALSE);
      }
    }

    private static boolean haveTheSameComments(PsiMethod method1, PsiMethod method2) {
      Set<String> text1 = collectCommentText(method1);
      Set<String> text2 = collectCommentText(method2);
      return text2.containsAll(text1);
    }

    private static Set<String> collectCommentText(PsiMethod method) {
      Set<String> result = new HashSet<>();
      PsiTreeUtil.processElements(method, child -> {
        if (child instanceof PsiComment psiComment && !(psiComment instanceof PsiDocComment)) {
          String text = TryWithIdenticalCatchesInspection.getCommentText(psiComment);
          if (!text.isEmpty()) {
            result.add(text);
          }
        }
        return true;
      });
      return result;
    }

    private boolean isSuperCallWithSameArguments(PsiCodeBlock body, PsiMethod method, PsiMethod superMethod) {
      final PsiStatement[] statements = body.getStatements();
      if (statements.length != 1) {
        return false;
      }
      final PsiStatement statement = statements[0];
      final PsiExpression expression;
      if (PsiTypes.voidType().equals(method.getReturnType())) {
        if (statement instanceof PsiExpressionStatement expressionStatement) {
          expression = expressionStatement.getExpression();
        }
        else {
          return false;
        }
      }
      else {
        if (statement instanceof PsiReturnStatement returnStatement) {
          expression = PsiUtil.skipParenthesizedExprDown(returnStatement.getReturnValue());
        }
        else {
          return false;
        }
      }
      if (!(expression instanceof PsiMethodCallExpression methodCallExpression)) {
        return false;
      }
      if (!MethodCallUtils.isSuperMethodCall(methodCallExpression, method)) {
        return false;
      }
      final PsiMethod targetMethod = methodCallExpression.resolveMethod();
      if (targetMethod != superMethod) {
        return false;
      }

      if (!collectCommentText(method).isEmpty()) {
        return false;
      }

      if (superMethod.hasModifierProperty(PsiModifier.PROTECTED)) {
        final PsiJavaFile file = (PsiJavaFile)method.getContainingFile();
        // implementing a protected method in another package makes it available to that package.
        PsiPackage aPackage = JavaPsiFacade.getInstance(method.getProject()).findPackage(file.getPackageName());
        if (aPackage == null) {
          return false; // when package statement is incorrect
        }
        final PackageScope scope = new PackageScope(aPackage, false, false);
        if (isOnTheFly()) {
          final PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(method.getProject());
          final PsiSearchHelper.SearchCostResult cost =
            searchHelper.isCheapEnoughToSearch(method.getName(), scope, null, null);
          if (cost == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES) {
            return true;
          }
          if (cost == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) {
            return false;
          }
        }
        final Query<PsiReference> search = ReferencesSearch.search(method, scope);
        final PsiClass containingClass = method.getContainingClass();
        for (PsiReference reference : search) {
          if (!PsiTreeUtil.isAncestor(containingClass, reference.getElement(), true)) {
            return false;
          }
        }
      }

      return areSameArguments(methodCallExpression, method);
    }

    private static boolean areSameArguments(PsiMethodCallExpression methodCallExpression, PsiMethod method) {
      // void foo(int param) { super.foo(42); } is not redundant
      PsiExpression[] arguments = methodCallExpression.getArgumentList().getExpressions();
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (arguments.length != parameters.length) return false;
      for (int i = 0; i < arguments.length; i++) {
        PsiExpression argument = arguments[i];
        PsiExpression exp = PsiUtil.deparenthesizeExpression(argument);
        if (!(exp instanceof PsiReferenceExpression)) return false;
        PsiElement resolved = ((PsiReferenceExpression)exp).resolve();
        if (!method.getManager().areElementsEquivalent(parameters[i], resolved)) {
          return false;
        }
      }
      return true;
    }
  }
}