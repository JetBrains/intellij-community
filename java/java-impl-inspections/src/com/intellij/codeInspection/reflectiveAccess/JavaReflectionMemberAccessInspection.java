// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reflectiveAccess;

import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.JavaLangClassMemberReference;
import com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.*;
import static com.intellij.psi.CommonClassNames.*;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

public final class JavaReflectionMemberAccessInspection extends AbstractBaseJavaLocalInspectionTool {

  private static final Set<String> MEMBER_METHOD_NAMES = Set.of(GET_FIELD, GET_DECLARED_FIELD,
                      GET_METHOD, GET_DECLARED_METHOD,
                      GET_CONSTRUCTOR, GET_DECLARED_CONSTRUCTOR);

  private final List<String> ignoredClassNames = new ArrayList<>();

  public boolean checkMemberExistsInNonFinalClasses = true;
  public String ignoredClassNamesString = JAVA_LANG_OBJECT + "," + JAVA_LANG_THROWABLE;

  public JavaReflectionMemberAccessInspection() {
    parseSettings();
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("ignoredClassNames", JavaBundle.message("inspection.reflection.member.access.check.exists.exclude.label"),
                 new JavaClassValidator().withTitle(JavaBundle.message("inspection.reflection.member.access.check.exists.exclude.chooser"))),
      checkbox("checkMemberExistsInNonFinalClasses", JavaBundle.message("inspection.reflection.member.access.check.exists"))
    );
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseSettings();
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    collectSettings();
    super.writeSettings(element);
  }

  private void parseSettings() {
    ignoredClassNames.clear();
    ContainerUtil.addAll(ignoredClassNames, ignoredClassNamesString.split(","));
  }

  private void collectSettings() {
    ignoredClassNamesString = String.join(",", ignoredClassNames);
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);

        final String referenceName = expression.getMethodExpression().getReferenceName();
        if (referenceName != null && MEMBER_METHOD_NAMES.contains(referenceName)) {
          final PsiMethod method = expression.resolveMethod();
          if (method != null) {
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass != null && JAVA_LANG_CLASS.equals(containingClass.getQualifiedName())) {
              switch (referenceName) {
                case GET_FIELD -> checkField(expression, false, holder);
                case GET_DECLARED_FIELD -> checkField(expression, true, holder);
                case GET_METHOD -> checkMethod(expression, false, holder);
                case GET_DECLARED_METHOD -> checkMethod(expression, true, holder);
                case GET_CONSTRUCTOR -> checkConstructor(expression, false, holder);
                case GET_DECLARED_CONSTRUCTOR -> checkConstructor(expression, true, holder);
              }
            }
          }
        }
      }
    };
  }

  private void checkField(@NotNull PsiMethodCallExpression callExpression, boolean isDeclared, @NotNull ProblemsHolder holder) {
    final PsiExpression[] arguments = callExpression.getArgumentList().getExpressions();
    if (arguments.length != 0) {
      final PsiExpression nameExpression = arguments[0];
      final String fieldName = getMemberName(nameExpression);
      if (fieldName != null) {
        final ReflectiveClass ownerClass = getOwnerClass(callExpression);
        if (ownerClass != null && ownerClass.isExact()) {
          final PsiField field = ownerClass.getPsiClass().findFieldByName(fieldName, true);
          if (field == null) {
            if (reportUnresolvedMembersOf(ownerClass)) {
              holder.registerProblem(nameExpression, JavaBundle.message(
                "inspection.reflection.member.access.cannot.resolve.field", fieldName));
            }
            return;
          }
          if (isDeclared && field.getContainingClass() != ownerClass.getPsiClass()) {
            LocalQuickFix fix = field.hasModifierProperty(PsiModifier.PUBLIC) ? new UseAppropriateMethodFix("getField") : null;
            holder.registerProblem(nameExpression, JavaBundle.message(
              "inspection.reflection.member.access.field.not.in.class", fieldName, ownerClass.getPsiClass().getQualifiedName()), LocalQuickFix.notNullElements(fix));
            return;
          }
          if (!isDeclared && !field.hasModifierProperty(PsiModifier.PUBLIC)) {
            holder.registerProblem(nameExpression, JavaBundle.message(
              "inspection.reflection.member.access.field.not.public", fieldName), new UseAppropriateMethodFix("getDeclaredField"));
          }
        }
      }
    }
  }

  private void checkMethod(@NotNull PsiMethodCallExpression callExpression, boolean isDeclared, @NotNull ProblemsHolder holder) {
    final PsiExpression[] arguments = callExpression.getArgumentList().getExpressions();
    if (arguments.length != 0) {
      final PsiExpression nameExpression = arguments[0];
      final String methodName = getMemberName(nameExpression);
      if (methodName != null) {
        final ReflectiveClass ownerClass = getOwnerClass(callExpression);
        if (ownerClass != null && ownerClass.isExact()) {
          final PsiMethod[] methods = ownerClass.getPsiClass().findMethodsByName(methodName, true);
          if (methods.length == 0) {
            if (reportUnresolvedMembersOf(ownerClass)) {
              holder.registerProblem(nameExpression, JavaBundle.message(
                "inspection.reflection.member.access.cannot.resolve.method", methodName));
            }
            return;
          }
          final PsiMethod matchingMethod = matchMethod(callExpression, methods, arguments, 1);
          if (matchingMethod == null) {
            if (reportUnresolvedMembersOf(ownerClass)) {
              holder.registerProblem(nameExpression, JavaBundle.message(
                "inspection.reflection.member.access.cannot.resolve.method.arguments", methodName));
            }
            return;
          }
          if (isDeclared && matchingMethod.getContainingClass() != ownerClass.getPsiClass()) {
            LocalQuickFix[] fix = matchingMethod.hasModifierProperty(PsiModifier.PUBLIC) ? new LocalQuickFix[] {new UseAppropriateMethodFix("getMethod")} : LocalQuickFix.EMPTY_ARRAY;
            holder.registerProblem(nameExpression, JavaBundle.message(
              "inspection.reflection.member.access.method.not.in.class", methodName, ownerClass.getPsiClass().getQualifiedName()), fix);
            return;
          }
          if (!isDeclared && !matchingMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
            holder.registerProblem(nameExpression, JavaBundle.message(
              "inspection.reflection.member.access.method.not.public", methodName), new UseAppropriateMethodFix("getDeclaredMethod"));
          }
        }
      }
    }
  }

  private void checkConstructor(@NotNull PsiMethodCallExpression callExpression,
                                boolean isDeclared,
                                @NotNull ProblemsHolder holder) {
    final ReflectiveClass ownerClass = getOwnerClass(callExpression);
    if (ownerClass != null && ownerClass.isExact()) {
      final PsiMethod[] methods = ownerClass.getPsiClass().getConstructors();
      final PsiExpression[] arguments = callExpression.getArgumentList().getExpressions();
      final PsiModifierListOwner constructorOrClass;
      if (methods.length != 0) {
        constructorOrClass = matchMethod(callExpression, methods, arguments, 0);
      }
      else {
        // implicit constructor
        constructorOrClass = arguments.length == 0 ? ownerClass.getPsiClass() : null;
      }
      if (constructorOrClass == null) {
        if (reportUnresolvedMembersOf(ownerClass)) {
          holder.registerProblem(callExpression.getArgumentList(), JavaBundle.message(
            "inspection.reflection.member.access.cannot.resolve.constructor.arguments"));
        }
        return;
      }
      if (!isDeclared && !constructorOrClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        holder.registerProblem(callExpression.getArgumentList(), JavaBundle.message(
          "inspection.reflection.member.access.constructor.not.public"), new UseAppropriateMethodFix("getDeclaredConstructor"));
      }
    }
  }

  private boolean reportUnresolvedMembersOf(@NotNull ReflectiveClass ownerClass) {
    return (checkMemberExistsInNonFinalClasses || ownerClass.getPsiClass().hasModifierProperty(PsiModifier.FINAL)) &&
           !ignoredClassNames.contains(ownerClass.getPsiClass().getQualifiedName());
  }

  @Contract("null->null")
  @Nullable
  private static String getMemberName(@Nullable PsiExpression memberNameArgument) {
    return computeConstantExpression(memberNameArgument, String.class);
  }

  @Nullable
  private static ReflectiveClass getOwnerClass(@NotNull PsiMethodCallExpression callExpression) {
    return getReflectiveClass(callExpression.getMethodExpression().getQualifierExpression());
  }

  @Nullable
  private static PsiMethod matchMethod(@NotNull PsiMethodCallExpression callExpression,
                                       PsiMethod[] methods,
                                       PsiExpression[] arguments,
                                       int argumentOffset) {
    final JavaReflectionInvocationInspection.Arguments methodArguments =
      JavaReflectionInvocationInspection.getActualMethodArguments(arguments, argumentOffset, MethodCallUtils.isVarArgCall(callExpression));
    if (methodArguments == null) {
      return null;
    }
    final List<ReflectiveType> argumentTypes =
      ContainerUtil.map(methodArguments.expressions(), JavaReflectionReferenceUtil::getReflectiveType);

    return JavaLangClassMemberReference.matchMethod(methods, argumentTypes);
  }

  static final class UseAppropriateMethodFix extends PsiUpdateModCommandQuickFix {
    private final String myProperMethod;

    UseAppropriateMethodFix(String method) {
      myProperMethod = method;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.use", myProperMethod + "()");
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.reflection.member.access.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiExpressionList expressionList = PsiTreeUtil.getNonStrictParentOfType(element, PsiExpressionList.class);
      if (expressionList == null) return;
      PsiMethodCallExpression call = ObjectUtils.tryCast(expressionList.getParent(), PsiMethodCallExpression.class);
      if (call == null) return;
      ExpressionUtils.bindCallTo(call, myProperMethod);
    }
  }
}
