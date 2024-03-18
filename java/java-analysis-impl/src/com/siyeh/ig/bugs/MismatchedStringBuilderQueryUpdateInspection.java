// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.*;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public final class MismatchedStringBuilderQueryUpdateInspection extends BaseInspection {

  @NonNls
  static final Map<BuilderType, Set<String>> returnSelfNames =
    Map.of(BuilderType.ABSTRACT_STRING_BUILDER,
           Set.of("append", "appendCodePoint", "delete", "deleteCharAt", "insert", "replace", "reverse", "repeat"),
           BuilderType.STRING_JOINER, Set.of("add", "merge", "setEmptyValue"));
  private static final String STRING_JOINER = "java.util.StringJoiner";

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "MismatchedQueryAndUpdateOfStringBuilder";
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final boolean updated = ((Boolean)infos[0]).booleanValue();
    final PsiType type = (PsiType)infos[1]; //"StringBuilder";
    if (updated) {
      return InspectionGadgetsBundle.message("mismatched.string.builder.updated.problem.descriptor", type.getPresentableText());
    }
    else {
      return InspectionGadgetsBundle.message("mismatched.string.builder.queried.problem.descriptor", type.getPresentableText());
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MismatchedQueryAndUpdateOfStringBuilderVisitor();
  }

  private static class MismatchedQueryAndUpdateOfStringBuilderVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      if (!field.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      BuilderType type = getType(field);
      if (type == null) {
        return;
      }
      final PsiClass containingClass = PsiUtil.getTopLevelClass(field);
      PsiExpression firstQualifier = getFirstQualifier(field.getInitializer(), type);
      if (!checkVariable(field, firstQualifier, containingClass)) {
        return;
      }
      final boolean queried = isStringBuilderQueried(field, containingClass, type);
      final boolean updated = isStringBuilderUpdated(field, firstQualifier, containingClass, type);
      if (queried == updated || UnusedSymbolUtil.isImplicitWrite(field)) {
        return;
      }
      registerFieldError(field, Boolean.valueOf(updated), field.getType());
    }

    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      BuilderType type = getType(variable);
      if (type == null) {
        return;
      }
      PsiExpression firstQualifier = getFirstQualifier(variable.getInitializer(), type);
      if (!checkVariable(variable, firstQualifier, codeBlock)) {
        return;
      }
      final boolean queried = isStringBuilderQueried(variable, codeBlock, type);
      final boolean updated = isStringBuilderUpdated(variable, firstQualifier, codeBlock, type);
      if (queried == updated) {
        return;
      }
      registerVariableError(variable, Boolean.valueOf(updated), variable.getType());
    }

    private static boolean checkVariable(@NotNull PsiVariable variable,
                                         @Nullable PsiExpression firstQualifier,
                                         @Nullable PsiElement context) {
      if (context == null) {
        return false;
      }
      if (!(PsiUtil.skipParenthesizedExprDown(firstQualifier) instanceof PsiNewExpression)) {
        return false;
      }
      if (VariableAccessUtils.variableIsAssigned(variable, context)) {
        return false;
      }
      if (VariableAccessUtils.variableIsAssignedFrom(variable, context)) {
        return false;
      }
      if (VariableAccessUtils.variableIsReturned(variable, context)) {
        return false;
      }
      if (VariableAccessUtils.variableIsPassedAsMethodArgument(variable, context)) {
        return false;
      }
      return !VariableAccessUtils.variableIsUsedInArrayInitializer(variable, context);
    }

    @Nullable
    private static PsiExpression getFirstQualifier(@Nullable PsiExpression expression, @NotNull BuilderType type) {
      if (expression instanceof PsiParenthesizedExpression parenthesizedExpression) {
        final PsiExpression next = parenthesizedExpression.getExpression();
        return getFirstQualifier(next, type);
      }
      else if (expression instanceof PsiMethodCallExpression methodCallExpression) {
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final String name = methodExpression.getReferenceName();
        if (name != null && returnSelfNames.get(type).contains(name)) {
          return getFirstQualifier(methodExpression.getQualifierExpression(), type);
        }
      }
      return expression;
    }

    private static boolean isStringBuilderUpdated(@NotNull PsiVariable variable, @Nullable PsiExpression firstQualifiedInitializer,
                                                  @NotNull PsiElement context, @NotNull BuilderType type) {
      if ((firstQualifiedInitializer != null &&
           !(ConstructionUtils.isEmptyStringBuilderInitializer(firstQualifiedInitializer) ||
           isOnlyDelimiterStringJoinerInitializer(firstQualifiedInitializer)))) {
        return true;
      }
      if (firstQualifiedInitializer != variable.getInitializer() && isChainUpdated(variable.getInitializer(), type)) {
        return true;
      }
      final StringBuilderUpdateCalledVisitor visitor = new StringBuilderUpdateCalledVisitor(variable, type);
      context.accept(visitor);
      return visitor.isUpdated();
    }

    private static boolean isChainUpdated(@Nullable PsiExpression expression, @NotNull BuilderType type) {
      if (expression instanceof PsiParenthesizedExpression parenthesizedExpression) {
        final PsiExpression next = parenthesizedExpression.getExpression();
        return isChainUpdated(next, type);
      }
      else if (expression instanceof PsiMethodCallExpression methodCallExpression) {
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final String name = methodExpression.getReferenceName();
        if (name != null && StringBuilderUpdateCalledVisitor.updateNames.get(type).contains(name)) {
          return true;
        }
        if (name != null && returnSelfNames.get(type).contains(name)) {
          return isChainUpdated(methodExpression.getQualifierExpression(), type);
        }
      }
      return false;
    }

    private static boolean isOnlyDelimiterStringJoinerInitializer(@NotNull PsiExpression construction) {
      construction = PsiUtil.skipParenthesizedExprDown(construction);
      if (!(construction instanceof PsiNewExpression newExpression)) return false;
      final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
      if (!ConstructionUtils.isReferenceTo(classReference, STRING_JOINER)) {
        return false;
      }
      final PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null) return false;
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) return false;
      final PsiExpression argument = arguments[0];
      final PsiType argumentType = argument.getType();
      return InheritanceUtil.isInheritor(argumentType, CommonClassNames.JAVA_LANG_CHAR_SEQUENCE);
    }

    private static boolean isStringBuilderQueried(@NotNull PsiVariable variable, @NotNull PsiElement context, @NotNull BuilderType type) {
      final StringBuilderQueryCalledVisitor visitor = new StringBuilderQueryCalledVisitor(variable, type);
      context.accept(visitor);
      return visitor.isQueried();
    }
  }

  private static class StringBuilderUpdateCalledVisitor extends JavaRecursiveElementWalkingVisitor {
    @NonNls
    private static final Map<BuilderType, Set<String>> updateNames =
      Map.of(BuilderType.ABSTRACT_STRING_BUILDER,
             Set.of("append", "appendCodePoint", "delete", "deleteCharAt", "insert", "replace", "reverse", "setCharAt", "setLength", "repeat"),
             BuilderType.STRING_JOINER, Set.of("add", "merge", "setEmptyValue"));

    @NotNull
    private final PsiVariable variable;
    private boolean updated;

    @NotNull
    private final BuilderType builderType;

    StringBuilderUpdateCalledVisitor(@NotNull PsiVariable variable, @NotNull BuilderType builderType) {
      this.variable = variable;
      this.builderType = builderType;
    }

    public boolean isUpdated() {
      return updated;
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      if (updated) return;
      super.visitMethodCallExpression(expression);
      checkReferenceExpression(expression.getMethodExpression());
    }

    @Override
    public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
      if (updated) return;
      super.visitMethodReferenceExpression(expression);
      checkReferenceExpression(expression);
    }

    private void checkReferenceExpression(PsiReferenceExpression methodExpression) {
      final String name = methodExpression.getReferenceName();
      if (name == null || !updateNames.get(builderType).contains(name)) return;
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (hasReferenceToVariable(variable, qualifierExpression, builderType)) {
        updated = true;
      }
    }
  }

  private static class StringBuilderQueryCalledVisitor extends JavaRecursiveElementWalkingVisitor {
    @NonNls
    private static final Map<BuilderType, Set<String>> queryNames =
      Map.of(BuilderType.ABSTRACT_STRING_BUILDER,
             Set.of("capacity", "charAt", "chars", "codePointAt", "codePointBefore", "codePointCount", "codePoints", "compareTo", "equals",
                    "getChars", "hashCode", "indexOf", "lastIndexOf", "length", "offsetByCodePoints", "subSequence", "substring",
                    "toString"),
             BuilderType.STRING_JOINER, Set.of("length", "toString"));

    @NotNull
    private final PsiVariable variable;
    private boolean queried;

    @NotNull
    private final BuilderType builderType;

    StringBuilderQueryCalledVisitor(@NotNull PsiVariable variable, @NotNull BuilderType type) {
      this.variable = variable;
      this.builderType = type;
    }

    public boolean isQueried() {
      return queried;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (queried) return;
      super.visitElement(element);
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      if (queried) return;
      super.visitReferenceExpression(expression);
      final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
      if (parent instanceof PsiPolyadicExpression polyadicExpression) {
        final IElementType tokenType = polyadicExpression.getOperationTokenType();
        if (!JavaTokenType.PLUS.equals(tokenType) || !ExpressionUtils.hasStringType(polyadicExpression)) {
          return;
        }
      }
      else if (!(parent instanceof PsiTemplate)) {
        return;
      }
      final PsiElement target = expression.resolve();
      if (!variable.equals(target)) {
        return;
      }
      queried = true;
    }

    @Override
    public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
      if (queried) return;
      super.visitMethodReferenceExpression(expression);
      final String name = expression.getReferenceName();
      if (name == null) return;
      if (!queryNames.get(builderType).contains(name) && !returnSelfNames.get(builderType).contains(name)) return;
      if (PsiTypes.voidType().equals(LambdaUtil.getFunctionalInterfaceReturnType(expression))) return;
      final PsiExpression qualifierExpression = expression.getQualifierExpression();
      if (hasReferenceToVariable(variable, qualifierExpression, builderType)) {
        queried = true;
      }
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      if (queried) return;
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (name == null) return;
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (!queryNames.get(builderType).contains(name)) {
        if (returnSelfNames.get(builderType).contains(name) && hasReferenceToVariable(variable, qualifierExpression, builderType) &&
            isVariableValueUsed(expression)) {
          queried = true;
        }
        return;
      }
      if (hasReferenceToVariable(variable, qualifierExpression, builderType)) {
        PsiElement parent = PsiTreeUtil.getParentOfType(expression, PsiStatement.class, PsiLambdaExpression.class);
        if (parent instanceof PsiStatement &&
            !SideEffectChecker.mayHaveSideEffects(
              parent, e -> e instanceof PsiMethodCallExpression && isSideEffectFreeBuilderMethodCall((PsiMethodCallExpression)e))) {
          return;
        }
        queried = true;
      }
    }

    /**
     * @param call call to check
     * @return true if method call has no side effect except possible modification of the current StringBuilder
     */
    private boolean isSideEffectFreeBuilderMethodCall(PsiMethodCallExpression call) {
      PsiReferenceExpression methodExpression = call.getMethodExpression();
      return !"getChars".equals(methodExpression.getReferenceName()) &&
             ExpressionUtils.isReferenceTo(methodExpression.getQualifierExpression(), variable);
    }

    private static boolean isVariableValueUsed(PsiExpression expression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiParenthesizedExpression parenthesizedExpression) {
        return isVariableValueUsed(parenthesizedExpression);
      }
      if (parent instanceof PsiPolyadicExpression polyadicExpression) {
        return isVariableValueUsed(polyadicExpression);
      }
      if (parent instanceof PsiTypeCastExpression typeCastExpression) {
        return isVariableValueUsed(typeCastExpression);
      }
      if (parent instanceof PsiReturnStatement) {
        return true;
      }
      if (parent instanceof PsiExpressionList) {
        final PsiElement grandParent = parent.getParent();
        if (grandParent instanceof PsiMethodCallExpression) {
          return true;
        }
      }
      else if (parent instanceof PsiArrayInitializerExpression) {
        return true;
      }
      else if (parent instanceof PsiAssignmentExpression assignmentExpression) {
        final PsiExpression rhs = assignmentExpression.getRExpression();
        return expression.equals(rhs);
      }
      else if (parent instanceof PsiVariable variable) {
        final PsiExpression initializer = variable.getInitializer();
        return expression.equals(initializer);
      }
      return false;
    }
  }

  @Nullable
  private static BuilderType getType(@NotNull PsiVariable variable) {
    if (TypeUtils.variableHasTypeOrSubtype(variable, CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER)) {
      return BuilderType.ABSTRACT_STRING_BUILDER;
    }
    if (TypeUtils.typeEquals(STRING_JOINER, variable.getType())) {
      return BuilderType.STRING_JOINER;
    }
    return null;
  }

  static boolean hasReferenceToVariable(@NotNull PsiVariable variable, @Nullable PsiElement element, @NotNull BuilderType type) {
    if (element instanceof PsiReferenceExpression referenceExpression) {
      return referenceExpression.isReferenceTo(variable);
    }
    else if (element instanceof PsiParenthesizedExpression parenthesizedExpression) {
      final PsiExpression expression = parenthesizedExpression.getExpression();
      return hasReferenceToVariable(variable, expression, type);
    }
    else if (element instanceof PsiMethodCallExpression methodCallExpression) {
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (name != null && returnSelfNames.get(type).contains(name)) {
        return hasReferenceToVariable(variable, methodExpression.getQualifierExpression(), type);
      }
    }
    else if (element instanceof PsiConditionalExpression conditionalExpression) {
      final PsiExpression thenExpression = conditionalExpression.getThenExpression();
      if (hasReferenceToVariable(variable, thenExpression, type)) {
        return true;
      }
      final PsiExpression elseExpression = conditionalExpression.getElseExpression();
      return hasReferenceToVariable(variable, elseExpression, type);
    }
    return false;
  }

  private enum BuilderType {ABSTRACT_STRING_BUILDER, STRING_JOINER}
}
